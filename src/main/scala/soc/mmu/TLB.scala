package soc.mmu

import bus.CPUBusReqType
import chisel3._
import chisel3.util._
import soc.cpu._
import utils.{SRAMSinglePort, SetAssocRandom}
import config._

case class MMUConfig
(
  VAddrBits: Int = 39,
  PAddrBits: Int = 32,
  AddrTranslationScheme: String = "Sv39"
) {
  // Just Support Sv39 now
  val PageTableLevel = AddrTranslationScheme.toLowerCase match {
    case "sv39"    => 3
    case t => throw new IllegalArgumentException(s"unknown address-translation scheme: $t")
  }

}

case class L1TLBConfig
(
  name: String = "iTLB",
  entrySize: Int = 8,
) {
  val isDTlb = name.toLowerCase match {
    case "itlb"    => false
    case "dtlb"    => true
    case t => throw new IllegalArgumentException("L1TLBConfig name field can only be iTLB or dTLB")
  }
}

trait MMUConst {
  val mmuCfg = Config.mmuConfig

  val VAddrBits = mmuCfg.VAddrBits
  val PAddrBits = mmuCfg.PAddrBits
  val PageTableLevel = mmuCfg.PageTableLevel

  val PageOffsetBits = 12
  val vpnBits = VAddrBits - PageOffsetBits
  val ppnBits = PAddrBits - PageOffsetBits

  val vpnSubBits = 9
  val LevelBits = log2Up(PageTableLevel)
  val XLEN = Config.XLEN

  def getVpn(v_addr: UInt) = v_addr(VAddrBits, PageOffsetBits)

}

class TLBReq extends Bundle with MMUConst {
  val v_addr = UInt(VAddrBits.W)
  val req_type = CPUBusReqType()
}

class TLBResp extends Bundle with MMUConst {
  val p_addr = UInt(PAddrBits.W)
  val isPF = new Bundle() {
    val load = Bool()
    val store = Bool()
    val instr = Bool()
  }
  val isAF = new Bundle() {
    val load = Bool()
    val store = Bool()
    val instr = Bool()
  }
}

class TLBMasterIO extends Bundle {
  val req = Flipped(DecoupledIO(new TLBReq))
  val resp = DecoupledIO(new TLBResp)
}

class PageTableEntry extends Bundle {
  val n = UInt(1.W)
  val pbmt = UInt(2.W)
  val reserved = UInt(7.W)
  val ppn = UInt(44.W)
  val rsw = UInt(2.W)
  val d = Bool()
  val a = Bool()
  val g = Bool()
  val u = Bool()
  val x = Bool()
  val w = Bool()
  val r = Bool()
  val v = Bool()

  def isLeafPTE() = {
    this.v && (this.r || this.x || this.w)
  }

  def isPageFault(level: UInt) = {
    !this.v || (!this.r && this.w)
  }

}

class TLBEntry extends Bundle with MMUConst {
  val tag = UInt(vpnBits.W) // virtual address vpn
  val pte = new PageTableEntry
  val pf = Bool()
  val level = UInt(LevelBits.W)

  def hit(vpn: UInt) = {
    this.tag === vpn
  }

  def apply(vpn: UInt, pte: PageTableEntry, pf: Bool, level: UInt) = {
    this.tag    := vpn
    this.pte    := pte
    this.pf     := pf
    this.level  := level
    this
  }
}

class TLBDataArrayReq extends Bundle with MMUConst {
  val vpn = UInt(vpnBits.W)
}

class TLBDataArrayResp extends Bundle with MMUConst {
  val pte = new PageTableEntry
  val pf = Bool()
  val miss = Bool()
}

class TLBDataArrayWrite extends Bundle with MMUConst {
  val entry = new TLBEntry
}

class TLBDataArray(cfg: L1TLBConfig) extends Module with MMUConst {
  val io = IO(new Bundle() {
    val r = new Bundle() {
      val req = Flipped(DecoupledIO(new TLBDataArrayReq))
      val resp = DecoupledIO(new TLBDataArrayResp)
    }
    val w = Flipped(ValidIO(new TLBDataArrayWrite))
  })

  val v = RegInit(VecInit(Seq.fill(cfg.entrySize)(false.B)))
  val data = RegInit(VecInit(Seq.fill(cfg.entrySize)(new TLBEntry)))

  val req_vpn = io.r.req.bits.vpn

  val s0_hitVec = v zip data map { case (v, d) =>
    v && d.hit(req_vpn)
  }

  val s0_tlb_entry = Mux1H(s0_hitVec, data)

  val s1_tlb_valid = RegNext(io.r.req.valid)
  val s1_tlb_entry = RegNext(s0_tlb_entry)
  val s1_hitVec = RegNext(VecInit(s0_hitVec))

  io.r.resp.valid := s1_tlb_valid
  io.r.resp.bits.pte := s1_tlb_entry.pte
  io.r.resp.bits.pf := s1_tlb_entry.pf
  io.r.resp.bits.miss := s1_tlb_valid && !s1_hitVec.asUInt.orR
}

class TLB(cfg: L1TLBConfig) extends Module with MMUConst {
  val io = IO(new Bundle() {
    val cpu = new TLBMasterIO
    val ptw = Flipped(new PTWMasterIO)
    val fromCSR = Input(new CSRtoMMUBundle)
  })

  val mode = if (cfg.isDTlb) io.fromCSR.d_mode else io.fromCSR.i_mode
  val satp = io.fromCSR.satp

  val vmEnable = satp.mode(satp.mode.getWidth - 1) && mode < Priv.M

  val dataArray = Module(new TLBDataArray(cfg))

  val s1_ready = Wire(Bool())

  /**
   * Stage 0
   */

  io.cpu.req.ready := s1_ready

  dataArray.io.r.req.valid := io.cpu.req.valid
  dataArray.io.r.req.bits.vpn := getVpn(io.cpu.req.bits.v_addr)

  /**
   * Stage 1
   */
  val s1_valid = RegNext(io.cpu.req.valid)
  val s1_bits = RegEnable(io.cpu.req.bits, io.cpu.req.fire)
  val s1_v_addr = RegEnable(io.cpu.req.bits.v_addr, io.cpu.req.fire)
  val pte = dataArray.io.r.resp.bits.pte
  val s1_p_addr = Cat(pte.ppn, s1_v_addr(PageOffsetBits-1, 0))
  val miss = vmEnable && dataArray.io.r.resp.bits.miss
  val pf = dataArray.io.r.resp.bits.pf

  io.cpu.resp.valid := RegNext(io.cpu.req.fire)
  io.cpu.resp.bits.p_addr := Mux(vmEnable, s1_p_addr, s1_v_addr)

  // PageFault Exception
  io.cpu.resp.bits.isPF.instr := pf && CPUBusReqType.isInstr(s1_bits.req_type)
  io.cpu.resp.bits.isPF.load  := pf && CPUBusReqType.isLoad(s1_bits.req_type)
  io.cpu.resp.bits.isPF.store := pf && CPUBusReqType.isStore(s1_bits.req_type)

  // TODO:
  io.cpu.resp.bits.isAF := DontCare

  /**
   * Miss handler
   */
  val s_idle :: s_ptwReq :: s_ptwResp :: s_replay :: Nil = Enum(4)
  val state = RegInit(s_idle)

  s1_ready := state === s_idle && io.cpu.resp.ready

  io.ptw.req.valid := state === s_ptwReq
  io.ptw.req.bits.vpn := s1_v_addr(PageOffsetBits+vpnBits-1, PageOffsetBits)

  dataArray.io.w.valid := state === s_ptwResp && io.ptw.resp.fire
  dataArray.io.w.bits.entry := (new TLBEntry).apply(vpn = io.ptw.resp.bits.vpn,
    pte = io.ptw.resp.bits.pte,
    pf = io.ptw.resp.bits.pf,
    level = io.ptw.resp.bits.level)

  switch (state) {
    is (s_idle) {
      when (s1_valid && miss) {
        state := s_ptwReq
      }
    }
    is (s_ptwReq) {
      when (io.ptw.req.fire) {
        state := s_ptwResp
      }
    }
    is (s_ptwResp) {
      when (io.ptw.resp.fire) {
        state := s_replay
      }
    }
    is (s_replay) {
      state := s_idle
    }
  }
}
