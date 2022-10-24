package soc.cpu

import chisel3._
import chisel3.util._
import config.Config._
import difftest.DifftestCSRState
import soc.ClintIO
import utils._

object CSROpType {
  val N = 0.U
  val R = 1.U
  val S = 2.U
  val C = 3.U
  val W = 4.U
}

trait CSRConst {
  val XLEN = 64
}

object Priv {
  val M = "b11".U
  val S = "b01".U
  val U = "b00".U
}

object ExceptionCode {
  def InstrAddrMisaligned = 0
  def InstrAccessFault = 1
  def IllegalInstr = 2
  def BreakPoint = 3
  def LoadAddrMisaligned = 4
  def LoadAccessFault = 5
  def StoreAddrMisaligned = 6
  def StoreAccessFault = 7
  def EcallU = 8
  def EcallVU = 8
  def EcallS = 9
  def EcallHS = 9
  def EcallVS = 10
  def EcallM = 11
  def InstrPageFault = 12
  def LoadPageFault = 13
  def StorePageFault = 15
  def InstrGuestPageFault = 20
  def LoadGuestPageFault = 21
  def VirtualInstruction = 22
  def StoreGuestPageFault = 23

  val total = 24

  def Priority = Seq(
    BreakPoint,
    InstrPageFault,
    InstrGuestPageFault,
    InstrAccessFault,
    IllegalInstr,
    VirtualInstruction,
    InstrAddrMisaligned,
    EcallM,
    EcallS,
    EcallVS,
    EcallU,
    StorePageFault,
    LoadPageFault,
    StoreGuestPageFault,
    LoadGuestPageFault,
    StoreAccessFault,
    LoadAccessFault,
    StoreAddrMisaligned,
    LoadAddrMisaligned
  )
}

object CSRAddr {
  // Machine Information Registers
  val mvendorid:  Int  = 0xF11
  val marchid:    Int  = 0xF12
  val mimpid:     Int  = 0xF13
  val mhartid:    Int  = 0xF14
  val mconfigptr: Int  = 0xF15

  // Machine Trap Setup
  val mstatus:    Int = 0x300
  val misa:       Int = 0x301
  val medeleg:    Int = 0x302
  val mideleg:    Int = 0x303
  val mie:        Int = 0x304
  val mtvec:      Int = 0x305
  val mcounteren: Int = 0x306

  // Machine Trap Handling
  val mscratch: Int = 0x340
  val mepc:     Int = 0x341
  val mcause:   Int = 0x342
  val mtval:    Int = 0x343
  val mip:      Int = 0x344
  val mtinst:   Int = 0x34a
  val mtval2:   Int = 0x34b
}

class MstatusBundle extends Bundle with CSRConst {
  val sd = UInt(1.W)
  val wpri4  = if (XLEN == 64) UInt(25.W) else null
  val mbe   = if (XLEN == 64) UInt(1.W) else null
  val sbe   = if (XLEN == 64) UInt(1.W) else null
  val sxl   = if (XLEN == 64) UInt(2.W)  else null
  val uxl   = if (XLEN == 64) UInt(2.W)  else null
  val wpri3 = if (XLEN == 64) UInt(9.W)  else UInt(8.W)
  val tsr   = UInt(1.W)
  val tw    = UInt(1.W)
  val tvm   = UInt(1.W)
  val mxr   = UInt(1.W)
  val sum   = UInt(1.W)
  val mprv  = UInt(1.W)
  val xs    = UInt(2.W)
  val fs    = UInt(2.W)
  val mpp   = UInt(2.W)
  val vs   = UInt(2.W)
  val spp   = UInt(1.W)
  val mpie = UInt(1.W)
  val ube = UInt(1.W)
  val spie = UInt(1.W)
  val wpri2 = UInt(1.W)
  val mie = UInt(1.W)
  val wpri1 = UInt(1.W)
  val sie = UInt(1.W)
  val wpri0 = UInt(1.W)

  assert(this.getWidth == XLEN)
}

class MtvecBundle extends Bundle{
  val base = UInt((XLEN - 2).W)
  val mode = UInt(2.W)
}

class SatpBundle extends Bundle {
  val mode = UInt(4.W)
  val asid = UInt(16.W)
  val zero = UInt(2.W)
  val ppn = UInt(44.W)

  def apply(satp: UInt): Unit = {
    require(satp.getWidth == XLEN)
    val s = satp.asTypeOf(new SatpBundle)
    mode := s.mode
    asid := s.asid
    ppn := s.ppn
  }
}

class CSRtoMMUBundle extends Bundle {
  val i_mode = UInt(2.W)
  val d_mode = UInt(2.W)
  val satp = new SatpBundle
}

class WBUtoCSRBundle extends Bundle {
  val pc = UInt(VAddrBits.W)
  val instr = UInt(32.W)
  val exceptions = Vec(ExceptionCode.total, Bool())
  val wdata = UInt(XLEN.W)
  val csr_cmd = WBCtrl()
  val rs1 = UInt(XLEN.W)
}

class CSRtoDecodeBundle extends Bundle {
  val csr_addr = Input(UInt(12.W))
  val csr_data = Output(UInt(XLEN.W))
}

//object RegMap {
//
//  def apply(addr: Int, reg: UInt, wfn: UInt => UInt = (x => x)): (Int, UInt, UInt => UInt) = {
//    (addr, reg, wfn)
//  }
//  def fullMask = (~(0.U(XLEN.W))).asUInt
//
//
//  def generate (mapping: Seq[(Int, UInt, UInt => UInt)], raddr: UInt, rdata : UInt,
//                waddr: UInt, wen: Bool, wdata: UInt, wmask: UInt = fullMask) = {
//    // write
//    mapping.map { case (a, r, wfn) =>
//      if (wfn != null) when(wen && waddr === a.U) { r := MaskData(r, wdata, wmask)  }
//    }
//    // read
//    rdata := MuxLookup(raddr, 0.U, mapping.map{ case(a, r, wfn) => (a.U === raddr) -> r })
//  }
//
//
//}


class CSR extends Module {
  val io = IO(new Bundle() {
    val wb = Flipped(ValidIO(new WBUtoCSRBundle))
    val intr = Input(new ClintIO)
    val read = new CSRtoDecodeBundle()
    val toMMU = Output(new CSRtoMMUBundle)
    val redirect = ValidIO(new RedirectIO)
  })

  val (valid, cmd, pc, rs1) = (io.wb.valid,
    io.wb.bits.csr_cmd, io.wb.bits.pc, io.wb.bits.rs1)

  /**
   * Mode: Just M S U mode
   */
  val priviledgeMode = RegInit(Priv.M)

  /**
   * M-mode CSR
   */
  val mstatus = RegInit("ha00000000".U.asTypeOf(new MstatusBundle))
  val mtvec   = RegInit(UInt(XLEN.W), 0.U)
  val mcause  = RegInit(UInt(XLEN.W), 0.U)
  val mepc    = Reg(UInt(XLEN.W))

  val mie     = RegInit(0.U(XLEN.W))
  val mip     = RegInit(0.U(XLEN.W))


  /**
   * S-mode CSR
   * TODO:
   */

//  val orig_csr = Wire(UInt(XLEN.W))
//  val write_data = MuxCase(rs1, Array(
//    (io.wb.bits.csr_cmd === WBCtrl.CSR_W)     -> rs1,
//    (io.wb.bits.csr_cmd === WBCtrl.CSR_S)     -> (orig_csr | rs1),
//    (io.wb.bits.csr_cmd === WBCtrl.CSR_C)     -> (orig_csr & (~rs1).asUInt)
//  ))

  /**
   * CSR Map
   */
  val csr_mapping = Seq(
    RegMap(CSRAddr.mstatus, mstatus.asUInt),
    RegMap(CSRAddr.mtvec, mtvec),
    RegMap(CSRAddr.mcause, mcause),
    RegMap(CSRAddr.mepc, mepc),
  )

  io.read := DontCare
  /**
   * Privileged Instruction
   * mret sret
   * ecall
   */
  val system_instr = cmd === WBCtrl.CSR_PRIV
  val funct12 = io.wb.bits.instr(31, 20)
  val isEcall = valid && system_instr && funct12 === "h000".U
  val isMret = valid && system_instr && funct12 === "h302".U
  val isXret = isMret

  val illegalMret = valid && isMret && priviledgeMode < Priv.M

  val retTarget = WireInit(0.U)

  when (isMret && !illegalMret) {
    mstatus.mie := mstatus.mpie
    priviledgeMode := mstatus.mpp
    mstatus.mpie := true.B
    mstatus.mpp := Priv.U
    when (mstatus.mpp =/= Priv.M) {
      mstatus.mprv := 0.U
    }
    retTarget := mepc(VAddrBits-1, 0)
  }

  /**
   * Exception And Interrupt
   */
  // Exception
  val exceptionVec = WireInit(io.wb.bits.exceptions)

  exceptionVec(isEcall) := isEcall

  val hasException = valid && exceptionVec.asUInt.orR
  val exceptionNo = ExceptionCode.Priority.foldRight(0.U)((i: Int, sum: UInt) => Mux(exceptionVec(i), i.U, sum))

  // TODO: Interrupt
  io.intr := DontCare
  val hasIntr = WireInit(false.B)
  val intrNo = WireInit(0.U)
  val hasIntrOrException = hasException || hasIntr
  val cause = Mux(hasIntr, intrNo, exceptionNo)
  val epc = Mux(hasIntr, pc, pc + 4.U)

  when (hasIntrOrException) {
    priviledgeMode := Priv.M

    mcause := cause
    mstatus.mpp := priviledgeMode
    mepc := epc
    mstatus.mpie := mstatus.mie
    mstatus.mie := false.B
  }

  /**
   * Redirect
   */
  val mtvecStruct = WireInit(mtvec.asTypeOf(new MtvecBundle))
  val trapTarget = Mux(mtvecStruct.mode === 0.U, Cat(mtvecStruct.base, Fill(2, 0.U)),
    Cat(mtvecStruct.base, Fill(2, 0.U)) + mcause << 2.U)
  io.redirect.valid := hasIntrOrException || isXret
  io.redirect.bits.target := Mux(hasIntrOrException, trapTarget, retTarget)

  /**
   * MMU Ctrl Signals
   */
  io.toMMU := DontCare  // TODO:

  /**
   * difftest csr
    */
  if (DiffTest) {
    val difftest_csr = Module(new DifftestCSRState)
    difftest_csr.suggestName("difftest_csr")

    difftest_csr.io.clock           := clock
    difftest_csr.io.coreid          := 0.U
    difftest_csr.io.priviledgeMode  := "b11".U
    difftest_csr.io.mstatus         := mstatus.asUInt
    difftest_csr.io.sstatus         := "h0000000200000000".U
    difftest_csr.io.mepc            := mepc
    difftest_csr.io.sepc            := 0.U
    difftest_csr.io.mtval           := 0.U
    difftest_csr.io.stval           := 0.U
    difftest_csr.io.mtvec           := mtvec
    difftest_csr.io.stvec           := 0.U
    difftest_csr.io.mcause          := mcause
    difftest_csr.io.scause          := 0.U
    difftest_csr.io.satp            := 0.U
    difftest_csr.io.mip             := 0.U
    difftest_csr.io.mie             := mie
    difftest_csr.io.mscratch        := 0.U
    difftest_csr.io.sscratch        := 0.U
    difftest_csr.io.mideleg         := 0.U
    difftest_csr.io.medeleg         := 0.U
  }

}
