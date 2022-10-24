package soc.cache

import chisel3._
import chisel3.util._
import bus._
import config._
import soc.cpu.ExceptionCode
import soc.mmu.TLBMasterIO
import utils.{MaskData, MaskExpand, ReplacementPolicy, SRAMSinglePort}

case class L1CacheConfig
(
  VAddrBits: Int = 39,
  PAddrBits: Int = 32,
  totalSize: Int = 4 * 1024,
  ways: Int = 4,
  L1AXIDataBits: Int = 64
) {

}

trait L1CacheConst {
  val l1cfg = Config.L1Config

  val VAddrBits = l1cfg.VAddrBits
  val PAddrBits = l1cfg.PAddrBits

  val totalSize = l1cfg.totalSize  // 4KB
  val Sets = 64
  val Ways =  l1cfg.ways
  val lineBits = 128
  val lineByte = lineBits / 8
  val IndexBits = log2Ceil(Sets)
  val numOfXLEN = lineBits / Config.XLEN

  val TagBits = VAddrBits - lineByte - IndexBits
  val lineAddrWidth = log2Ceil(lineByte)
  val SetAddrLength = log2Up(Sets)

  val L1AXIDataBits = l1cfg.L1AXIDataBits
  val AXIBeats = lineBits / L1AXIDataBits

  def getIndex(addr: UInt) = addr(IndexBits + lineBits - 1, lineBits)
  def getTag(addr: UInt) = addr(addr.getWidth - 1, IndexBits + lineBits)
  def getXlenOffset(addr: UInt) = addr(lineAddrWidth - 1, 3)

  def addrMatch(addr: UInt, addrSpace: List[(Long, Long)]) = {
    val matchVec = VecInit(addrSpace.map(
      range => (addr >= range._1.U && addr < (range._1 + range._2).U)))
    matchVec.asUInt.orR
  }
}

class L1DataArrayTemplateReq[T <: Data](_type: T) extends Bundle with L1CacheConst {
  val addr = UInt(SetAddrLength.W)
  val wdata = _type
  val wen = Bool()
  val way_en = UInt(Ways.W)
}

class L1DataArrayTemplateResp[T <: Data](_type: T) extends Bundle with L1CacheConst {
  val rdata = Vec(Ways, _type)
}

class L1DataArrayTemplateBus[T <: Data](_type: T) extends Bundle with L1CacheConst {
  val req = Flipped(DecoupledIO(new L1DataArrayTemplateReq(_type)))
  val resp = DecoupledIO(new Bundle() {
    val rdata = _type
  })
}

class L1CacheDataArray[T <: Data](_type: T) extends Module with L1CacheConst {
  val io = IO(new Bundle() {
    val req = Flipped(DecoupledIO(new L1DataArrayTemplateReq(_type)))
    val resp = DecoupledIO(new L1DataArrayTemplateResp(_type))
  })

  val array = Seq.fill(Ways)(Module(new SRAMSinglePort(_type, Sets, true)))

  for (i <- 0 until Ways) {
    array(i).io.addr := io.req.bits.addr
    array(i).io.wen := io.req.valid && io.req.bits.wen && io.req.bits.way_en(i)
    array(i).io.wdata := io.req.bits.wdata

    io.resp.bits.rdata(i) := array(i).io.rdata
  }

}

class MetaBundle extends Bundle with L1CacheConst {
  val tag = UInt(TagBits.W)
  val valid = Bool()
  val dirty = Bool()

  def apply(tag: UInt, dirty: Bool, valid: Bool) = {
    this.tag := tag
    this.dirty := dirty
    this.valid := valid
    this
  }
}

class DataBundle extends Bundle with L1CacheConst {
  val data = Vec(numOfXLEN, UInt(Config.XLEN.W))
}

class L1Cache extends Module with L1CacheConst {
  val io = IO(new Bundle() {
    val cpu = Flipped(new MasterCpuLinkBus)
    val tlb = Flipped(new TLBMasterIO)
    val uncache = new MasterCpuLinkBus
    val mem = new AXI4
  })

  /**
   * L1 Cahce Components: DataArray MetaArray
   * DataArray:
   * MetaArray:
   */
  val dataArray = Module(new L1CacheDataArray(new DataBundle))
  val metaArray = Module(new L1CacheDataArray(new MetaBundle))

  val metaArb = Module(new Arbiter(new L1DataArrayTemplateReq(new MetaBundle), 2))

  val s2_ready = Wire(Bool())
  /**
   * Stage 0: virual address
   */
  val s0_fire = io.cpu.req.fire

  io.tlb.req.valid := io.cpu.req.valid
  io.tlb.req.bits.v_addr := io.cpu.req.bits.addr
  io.tlb.req.bits.req_type := io.cpu.req.bits.id

  /**
   * Stage 1:
   */
  val refillArbIndex = 0
  val s1ReqIndex = 1
  val dataArb = Module(new Arbiter(new L1DataArrayTemplateReq(new DataBundle), 2))

  val s1_valid = io.cpu.req.valid
  val s1_cmd = RegEnable(next = io.cpu.req.bits.cmd, init = 0.U, enable = s0_fire)
  val s1_wdata = RegEnable(next = io.cpu.req.bits.wdata, init = 0.U, enable = s0_fire)
  val s1_strb = RegEnable(next = io.cpu.req.bits.strb, init = 0.U, enable = s0_fire)
  val s1_id = RegEnable(next = io.cpu.req.bits.id, init = 0.U, enable = s0_fire)
  val s1_p_addr = io.tlb.resp.bits.p_addr

  val s1_exceptionVec = WireInit(0.U.asTypeOf(Vec(ExceptionCode.total, Bool())))
  s1_exceptionVec(ExceptionCode.InstrPageFault) := io.tlb.resp.bits.isPF.instr
  s1_exceptionVec(ExceptionCode.LoadPageFault)  := io.tlb.resp.bits.isPF.instr
  s1_exceptionVec(ExceptionCode.StorePageFault) := io.tlb.resp.bits.isPF.instr

  val cacheable = addrMatch(addr = s1_p_addr, addrSpace = Config.cacheableAddrSpace)   // TODO: fix it by pmp
  io.tlb.resp.ready := s2_ready
  val s1_fire = io.tlb.resp.fire && cacheable

  dataArb.io.in(s1ReqIndex).valid := s1_fire
  dataArb.io.in(s1ReqIndex).bits.addr := getIndex(s1_p_addr)
  dataArb.io.in(s1ReqIndex).bits.wen := false.B
  dataArb.io.in(s1ReqIndex).bits.wdata := DontCare
  dataArb.io.in(s1ReqIndex).bits.way_en := ((1 << Ways)-1).U

  metaArb.io.in(s1ReqIndex).valid := s1_fire
  metaArb.io.in(s1ReqIndex).bits.addr := getIndex(s1_p_addr)
  metaArb.io.in(s1ReqIndex).bits.wen := false.B
  metaArb.io.in(s1ReqIndex).bits.wdata := DontCare
  metaArb.io.in(s1ReqIndex).bits.way_en := ((1 << Ways)-1).U

  dataArray.io.req <> dataArb.io.out
  metaArray.io.req <> metaArb.io.out

  /**
   * Stage 2: Determines whether the cache reading is hit
   *
   */
  val s2_p_addr = RegEnable(s1_p_addr, s1_fire)
  val s2_valid  = RegNext(s1_fire)
  val s2_cmd    = RegEnable(next = s1_cmd, init = 0.U, enable = s1_fire)
  val s2_wdata  = RegEnable(next = s1_wdata, init = 0.U, enable = s1_fire)
  val s2_strb   = RegEnable(next = s1_strb, init = 0.U, enable = s1_fire)
  val s2_id     = RegEnable(next = s1_id, init = 0.U, enable = s1_fire)
  val s2_exceptionVec = RegEnable(next = s1_exceptionVec, enable = s1_fire)
  val hasException = s2_exceptionVec.asUInt.orR

  val readLines = dataArray.io.resp.bits.rdata
  val metaLines = metaArray.io.resp.bits.rdata
  val hitVec = metaLines.map(meta => meta.valid && meta.tag === getTag(s2_p_addr))
  val hasHit = hitVec.reduce(_|_)
  val hitWay = OHToUInt(hitVec)
  val hitLine = readLines(hitWay).data
  val hitXlenData = hitLine(getXlenOffset(s2_p_addr))

  val miss = s2_valid && !hasHit && !hasException

  val replace = ReplacementPolicy.fromString("random", Sets, Ways)
  val replaceWay = replace.way(getIndex(s2_p_addr))
  val writeBackLineAddr = Cat(metaLines(replaceWay).tag, getIndex(s2_p_addr), 0.U(lineByte.W))
  val replaceLineIsDirty = metaLines(replaceWay).dirty && metaLines(replaceWay).valid

  // store event
  val wmask = MaskExpand(s2_strb)
  val xlenAfterWrite = MaskData(hitXlenData, s2_wdata, wmask)
  val updateLine = {
    val newLine = WireInit(hitLine)
    newLine(getXlenOffset(s2_p_addr)) := xlenAfterWrite
    newLine
  }

  /**
   * Resp to CPU
   */

  io.cpu.resp.valid := (state === s_idle && !miss) || io.uncache.resp.valid
  val cacheResp = Wire(new CpuLinkResp)
  cacheResp.data := hitXlenData
  cacheResp.id := s2_id
  cacheResp.cmd := Mux(CpuLinkCmd.isWriteReq(s2_cmd), CpuLinkCmd.resp_write, CpuLinkCmd.req_read)

  val uncacheResp = io.uncache.resp.bits
  io.cpu.resp.bits := Mux(io.uncache.resp.valid, uncacheResp, cacheResp)

  /**
   * Cache Miss Handler Unit
   * The following code is the processing logic in case of Cache line missing
   */
  val s_idle :: s_memReadReq :: s_memReadResp :: s_memWriteAddrReq :: s_memWriteDataReq :: s_memWriteResp :: s_refill :: s_replay :: Nil = Enum(8)
  val state = RegInit(s_idle)
  s2_ready := state === s_idle && io.uncache.req.ready && io.cpu.resp.ready

  val axiBeatsReg = Counter(AXIBeats)
  val lineBuffer = RegInit(VecInit(Seq.fill(AXIBeats)(0.U(L1AXIDataBits.W))))

  io.mem.ar.valid := state === s_memReadReq
  io.mem.ar.bits.addr := s2_p_addr
  io.mem.ar.bits.id := 0.U
  io.mem.ar.bits.len := (AXIBeats - 1).U
  io.mem.ar.bits.size := lineAddrWidth.U
  io.mem.ar.bits.burst := 0.U

  io.mem.aw.valid := state === s_memWriteAddrReq
  io.mem.aw.bits.addr := writeBackLineAddr
  io.mem.aw.bits.len := (AXIBeats - 1).U
  io.mem.aw.bits.burst := 0.U

  io.mem.w.valid := state === s_memWriteDataReq
  io.mem.w.bits.data := lineBuffer(axiBeatsReg.value)
  io.mem.w.bits.last := axiBeatsReg.value === (AXIBeats - 1).U
  io.mem.w.bits.strb := "hff".U

  dataArb.io.in(refillArbIndex).valid := state === s_refill
  dataArb.io.in(refillArbIndex).bits.wen := state === s_refill
  dataArb.io.in(refillArbIndex).bits.way_en := UIntToOH(replaceWay)
  dataArb.io.in(refillArbIndex).bits.addr := getIndex(s2_p_addr)
  dataArb.io.in(refillArbIndex).bits.wdata := lineBuffer

  metaArb.io.in(refillArbIndex).valid := state === s_refill
  metaArb.io.in(refillArbIndex).bits.wen := state === s_refill
  metaArb.io.in(refillArbIndex).bits.way_en := UIntToOH(replaceWay)
  metaArb.io.in(refillArbIndex).bits.addr := getIndex(s2_p_addr)
  metaArb.io.in(refillArbIndex).bits.wdata := (new MetaBundle).apply(tag = getTag(s2_p_addr), dirty = false.B, valid = true.B)

  switch (state) {
    is (s_idle) {
      when (miss) {
        when (replaceLineIsDirty) {
          state := s_memWriteAddrReq
          lineBuffer := readLines(replaceWay)
        }.otherwise {
          state := s_memReadReq
        }
      }
    }
    is (s_memReadReq) {
      when (io.mem.ar.fire) {
        state := s_memReadResp
      }
    }
    is (s_memReadResp) {
      when (io.mem.r.fire) {
        lineBuffer(axiBeatsReg.value) := io.mem.r.bits.data
        axiBeatsReg.inc()
        when (io.mem.r.bits.last) {
          state := s_refill
          axiBeatsReg.reset()
        }
      }
    }
    is (s_memWriteAddrReq) {
      when (io.mem.aw.fire) {
        state := s_memWriteDataReq
      }
    }
    is (s_memWriteDataReq) {
      when (io.mem.w.fire) {
        axiBeatsReg.inc()
        when (io.mem.w.bits.last) {
          state := s_memWriteResp
          axiBeatsReg.reset()
        }
      }
    }
    is (s_memWriteResp) {
      when (io.mem.b.fire) {
        state := s_memReadReq
      }
    }
    is (s_refill) {
      when (dataArb.io.in(refillArbIndex).fire) {
        state := s_replay
      }
    }
    is (s_replay) {
      state := s_idle
    }
  }

}
