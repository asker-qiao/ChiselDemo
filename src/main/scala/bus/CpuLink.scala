package bus

import chisel3._
import chisel3.util._
import soc.cpu._
import config._

object CpuLinkCmd {
  def apply() = UInt(2.W)

  def req_read    = "b00".U
  def req_write   = "b01".U
  def resp_read   = "b10".U
  def resp_write  = "b10".U

  def isWriteReq(cmd: UInt) = cmd === req_write
}

object CPUBusReqType {
  def apply() = UInt(2.W)

  val instr = 0.U
  val load = 1.U
  val store = 2.U

  def isInstr(_type: UInt) = _type === this.instr
  def isLoad(_type: UInt) = _type === this.load
  def isStore(_type: UInt) = _type === this.store
  def isDTLBReq(_type: UInt) = _type === this.load && _type === this.store
}

trait CpuLinkConst {
  val idBits = 4
  val typeBits = 2
}

class CpuLinkBundle extends Bundle with CpuLinkConst

class SimpleBusInstrReq extends CpuLinkBundle {
  val addr = UInt(Config.AddrBits.W)
}

class SimpleBusInstrResp extends Bundle {
  val data = UInt(Config.XLEN.W)
  val exception = Vec(ExceptionCode.total, Bool())
}

class CpuLinkReq extends CpuLinkBundle {
  val addr = UInt(Config.AddrBits.W)
  val id = CPUBusReqType()
  val cmd = CpuLinkCmd()
  val wdata = UInt(Config.XLEN.W)
  val strb = UInt(8.W)
  val size = UInt(2.W)

  def apply(addr: UInt, id: UInt, cmd: UInt, size: UInt, wdata: UInt = 0.U, strb: UInt = 0.U) = {
    this.addr   := addr
    this.id     := id
    this.cmd    := cmd
    this.size   := size
    this.wdata  := wdata
    this.strb   := strb
  }
}

class CpuLinkResp extends CpuLinkBundle {
  val data = UInt(Config.XLEN.W)
  val cmd = CpuLinkCmd()
  val id = CPUBusReqType()
  val exception = Vec(ExceptionCode.total, Bool())

  def apply(data: UInt, id: UInt, cmd: UInt) = {
    this.data := data
    this.id := id
    this.cmd := cmd
  }
}

class MasterCpuLinkBus extends CpuLinkBundle {
  val req = DecoupledIO(new CpuLinkReq)
  val resp = Flipped(DecoupledIO(new CpuLinkResp))
}

class DoubleCpuLink extends Bundle {
  val imem = new MasterCpuLinkBus
  val dmem = new MasterCpuLinkBus
}

class CpuLinkCrossBar1toN(addrSpace: List[(Long, Long)]) extends Module with CpuLinkConst {
  val numOut = addrSpace.length
  val io = IO(new Bundle() {
    val in = Flipped(new MasterCpuLinkBus)
    val out = Vec(numOut, new MasterCpuLinkBus)
  })

  val req = io.in.req

  val addr = req.bits.addr
  val outMatchVec = VecInit(addrSpace.map(
    range => (addr >= range._1.U && addr < (range._1 + range._2).U)))
  val outSelVec = VecInit(PriorityEncoderOH(outMatchVec))

  val queueSize = addrSpace.length
  val idReg = RegInit(VecInit(Seq.fill(queueSize)(0.U(idBits.W))))

  req.ready := io.out.zip(outSelVec).map { case (o, m) => o.req.ready && m }.reduce(_|_)

  for (i <- 0 until numOut) {
    val out = io.out(i)

    out.req.valid := req.valid && outSelVec(i)
    out.req.bits.apply(addr = req.bits.addr, id = req.bits.id, cmd = req.bits.cmd, size = req.bits.size,
      wdata = req.bits.wdata, strb = req.bits.strb)
  }

  // resp
  val respArb = Module(new Arbiter(new CpuLinkResp, numOut))
  respArb.io.in.zip(io.out).map{ case (in, out) => in <> out}
  io.in.resp <> respArb.io.out
}