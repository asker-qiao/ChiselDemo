package bus

import chisel3._
import chisel3.util._
import soc.cpu._
import config._

object SimpleBusCmd {
  def apply() = UInt(2.W)

  def req_read    = "b00".U
  def req_write   = "b01".U
  def resp_read   = "b10".U
  def resp_write  = "b10".U

  def isWriteReq(cmd: UInt) = cmd === req_write
}

trait SimpleBusConst {
  val idBits = 4
}

class SimpleBusBundle extends Bundle with SimpleBusConst

class SimpleBusInstrReq extends SimpleBusBundle {
  val addr = UInt(Config.AddrBits.W)
}

class SimpleBusInstrResp extends Bundle {
  val data = UInt(Config.XLEN.W)
}

class SimpleBusReq extends SimpleBusBundle {
  val addr = UInt(Config.AddrBits.W)
  val id = UInt(idBits.W)
  val cmd = SimpleBusCmd()
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

class SimpleBusResp extends SimpleBusBundle {
  val data = UInt(Config.XLEN.W)
  val id = UInt(idBits.W)
  val cmd = SimpleBusCmd()

  def apply(data: UInt, id: UInt, cmd: UInt) = {
    this.data := data
    this.id := id
    this.cmd := cmd
  }
}

class MasterSimpleBus extends SimpleBusBundle {
  val req = DecoupledIO(new SimpleBusReq)
  val resp = Flipped(DecoupledIO(new SimpleBusResp))
}

class InstrFetchBus extends Bundle {
  val req   = Decoupled(new SimpleBusInstrReq)
  val resp  = Flipped(DecoupledIO(new SimpleBusInstrResp))
}

class SimpleBusAccessMemReq extends Bundle {
  val addr  = UInt(Config.AddrBits.W)
  val wdata = UInt(Config.XLEN.W)
  val strb  = UInt((Config.XLEN / 8).W)
  val cmd   = SimpleBusCmd()
  // val wen   = Bool()
}

class SimpleBusAccessMemResp extends Bundle {
  val rdata = UInt(Config.XLEN.W)
}

class AccessMemBus extends Bundle {
  val req   = Decoupled(new SimpleBusAccessMemReq)
  val resp  = Flipped(DecoupledIO(new SimpleBusAccessMemResp))
}

class DoubleSimpleBus extends Bundle {
  val imem = new InstrFetchBus
  val dmem = new AccessMemBus
  // override def cloneType: SimpleBus.this.type = 
  //   new SimpleBus(num_read, num_write).asInstanceOf[this.type]
}

class SimpleBusCrossBar1toN(addrSpace: List[(Long, Long)]) extends Module with SimpleBusConst {
  val numOut = addrSpace.length
  val io = IO(new Bundle() {
    val in = Flipped(new MasterSimpleBus)
    val out = Vec(numOut, new MasterSimpleBus)
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
  val respArb = Module(new Arbiter(new SimpleBusResp, numOut))
  respArb.io.in.zip(io.out).map{ case (in, out) => in <> out}
  io.in.resp <> respArb.io.out
}