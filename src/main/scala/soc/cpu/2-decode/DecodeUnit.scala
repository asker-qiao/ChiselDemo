package cpu

import chisel3._
import chisel3.util._
import Config.XLEN

object InstrType {
  def width = 3
  val illegal = "b0000".U
  val i       = "b0001".U
  val s       = "b0010".U
  val u       = "b0011".U
  val b       = "b0100".U
  val j       = "b0101".U
  val r       = "b0110".U
  val z       = "b0111".U
  val trap    = "b1000".U
  def apply() = UInt(width.W)

  def genImm(instr: UInt, instr_type: UInt): UInt = {
    //assert (sel.getWidth == 3)
    // imm
    val imm_i = instr(31, 20)
    val imm_s = Cat(instr(31, 25), instr(11, 7))
    val imm_b = Cat(instr(31), instr(7), instr(30, 25), instr(11, 8))
    val imm_u = instr(31, 12)
    val imm_j = Cat(instr(31), instr(19, 12), instr(20) , instr(30, 21))
    val imm_z = Cat(Fill(XLEN - 5, 0.U), instr(19,15))
    // output sign-extend immediates
    val imm_i_sext = Cat(Fill(XLEN - 12, imm_i(11)), imm_i)
    val imm_s_sext = Cat(Fill(XLEN - 12, imm_s(11)), imm_s)
    val imm_b_sext = Cat(Fill(XLEN - 13, imm_b(11)), imm_b, 0.U)
    val imm_u_sext = Cat(Fill(XLEN - 32, imm_u(19)), imm_u, Fill(12, 0.U))
    val imm_j_sext = Cat(Fill(XLEN - 21, instr(31)), imm_j, 0.U)

    MuxCase(0.U, Array(
      (instr_type === InstrType.i)   -> imm_i_sext,
      (instr_type === InstrType.s)   -> imm_s_sext,
      (instr_type === InstrType.b)   -> imm_b_sext,
      (instr_type === InstrType.u)   -> imm_u_sext,
      (instr_type === InstrType.j)   -> imm_j_sext,
    ))
  }
}

object JBUType {
  // Branch Type
  def none   = "b0000".U  // Next
  def bne    = "b0001".U  // Branch on NotEqual
  def beq    = "b0010".U  // Branch on Equal
  def bge    = "b0011".U  // Branch on Greater/Equal
  def bgeu   = "b0100".U  // Branch on Greater/Equal Unsigned
  def blt    = "b0101".U  // Branch on Less Than
  def bltu   = "b0110".U  // Branch on Less Than Unsigned
  def jal    = "b1000".U  // Jump
  def jalr   = "b1001".U  // Jump Register

  def apply() = UInt(4.W)
  def isJump(jb_type: UInt) = jb_type(3)
  def isBranch(jb_type: UInt) = !jb_type(3) && jb_type =/= JBUType.none
}

object SrcType {
  def width = 2
  val no = 0.U(width.W)
  val reg = 1.U(width.W)
  val pc = 2.U(width.W)
  val imm = 3.U(width.W)

  def apply() = UInt(width.W)
}

object InstValid {
  val N = 0.U(1.W)
  val Y = 1.U(1.W)
}

object WBType {
  def width = 2
  val not = 0.U
  val exe = 1.U
  val pc = 2.U
  val mem = 3.U

  def apply() = UInt(width.W)
}

object FuType {
  def width = 2
  val alu = 0.U
  val jbu = 1.U
  val csr = 2.U
  val mdu = 3.U

  def apply() = UInt(width.W)
  def isALU(fu_type: UInt) = fu_type === FuType.alu
  def isJBU(fu_type: UInt) = fu_type === FuType.jbu
  def isMDU(fu_type: UInt) = fu_type === FuType.mdu
  def isCSR(fu_type: UInt) = fu_type === FuType.csr
}

object FuOpType {
  def apply() = UInt(4.W)
}

object MemType {
  def N = 0.U(1.W)
  def Y = 1.U(1.W)
}


object RfWen {
  val N = 0.U(1.W)
  val Y = 1.U(1.W)
}

object Decode {
  val idx_rs1_type = 1
  val idx_rs2_type = 2
  val idx_fu_type = 3
  val idx_fu_func = 4
  val idx_mem_en = 5
  val idx_mem_op = 6
  val idx_wb_type = 7
  val idx_rf_wen = 8

  val decodeDefault = List(InstrType.illegal, SrcType.no, SrcType.no, FuType.alu, ALUOpType.ADD, MemType.N, MemOpType.no, WBType.exe, RfWen.N)

  def DecodeTable = RV64I.table ++ SelfDefineTrap.table
}

trait RISCVConstants {
  // abstract out instruction decode magic numbers
  val RD_MSB  = 11
  val RD_LSB  = 7
  val RS1_MSB = 19
  val RS1_LSB = 15
  val RS2_MSB = 24
  val RS2_LSB = 20

  val CSR_ADDR_MSB = 31
  val CSR_ADDR_LSB = 20
}


class DecodeUnit extends Module with RISCVConstants {
  val io = IO(new Bundle() {
    val in = Flipped(DecoupledIO(new FetchInstrInfo))
    val out = DecoupledIO(new DecodeCtrlSignal)
    // read reg file
    val read = Vec(2, Flipped(new RegfileReadIO))
    // bypass: come from exe, mem, wb stage
    // val bypass = Vec(3, Input(new ByPassIO))
  })

  io.in.ready := io.out.ready

  val (valid, instr, pc) = (io.in.valid, io.in.bits.instr, io.in.bits.pc)
  // decode instruction
  val ctrlsignals = ListLookup(instr, Decode.decodeDefault, Decode.DecodeTable)

  // ctrl signals
  val instr_type  = ctrlsignals(0)
  val op1_sel     = ctrlsignals(Decode.idx_rs1_type)
  val op2_sel     = ctrlsignals(Decode.idx_rs2_type)
  val fu_type     = ctrlsignals(Decode.idx_fu_type)
  val fu_func     = ctrlsignals(Decode.idx_fu_func)
  val mem_en      = ctrlsignals(Decode.idx_mem_en)
  val mem_op      = ctrlsignals(Decode.idx_mem_op)
  val rf_wen      = ctrlsignals(Decode.idx_rf_wen)

  // src reg
  val rs1_addr = instr(RS1_MSB, RS1_LSB)  // rs1
  val rs2_addr = instr(RS2_MSB, RS2_LSB)  // rs2
  val wb_addr = instr(RD_MSB, RD_LSB)     // rd

  // read the regfile
  io.read(0).addr := rs1_addr
  io.read(1).addr := rs2_addr

  // // bypass
  val rs1_data = io.read(0).data
  val rs2_data = io.read(1).data

  // val rs1_data = MuxCase(io.read(0).data, Array(
  //   (io.bypass(0).wb_addr === rs1_addr && (rs1_addr =/= 0.U) && io.bypass(0).rf_wen)  -> io.bypass(0).wb_data,
  //   (io.bypass(1).wb_addr === rs1_addr && (rs1_addr =/= 0.U) && io.bypass(1).rf_wen)  -> io.bypass(1).wb_data,
  // ))
  // val rs2_data = MuxCase(io.read(1).data, Array(
  //   (io.bypass(0).wb_addr === rs2_addr && (rs2_addr =/= 0.U) && io.bypass(0).rf_wen)  -> io.bypass(0).wb_data,
  //   (io.bypass(1).wb_addr === rs2_addr && (rs2_addr =/= 0.U) && io.bypass(1).rf_wen)  -> io.bypass(1).wb_data,
  // ))

  val imm = InstrType.genImm(instr, instr_type)

  // generate operand 
  val op1_data = MuxCase(0.U, Array(
    (op1_sel === SrcType.reg)   -> rs1_data,
    (op1_sel === SrcType.imm)   -> imm,
    (op1_sel === SrcType.pc)    -> pc,
  )).asUInt()
  val op2_data = MuxCase(0.U, Array(
    (op2_sel === SrcType.reg)   -> rs2_data,
    (op2_sel === SrcType.imm)   -> imm,
    (op2_sel === SrcType.pc)    -> pc,
  )).asUInt()

  // output to next stage
  io.out.valid         := io.in.valid
  io.out.bits.pc       := pc
  io.out.bits.instr    := instr
  io.out.bits.op1_data := op1_data
  io.out.bits.op2_data := op2_data
  io.out.bits.rs2_data := io.read(1).data
  io.out.bits.fu_type  := fu_type
  io.out.bits.fu_func  := fu_func
  io.out.bits.mem_en   := mem_en
  io.out.bits.mem_op   := mem_op
  io.out.bits.wb_addr  := wb_addr
  io.out.bits.wb_type  := ctrlsignals(Decode.idx_wb_type)
  io.out.bits.rf_wen   := rf_wen
}