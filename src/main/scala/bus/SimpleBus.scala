package bus

import chisel3._
import chisel3.util._
import cpu._

object SimpleBusCmd {
  def apply() = UInt(2.W)

  def req_read    = "b00".U
  def req_write   = "b01".U
  def resp_read   = "b10".U
  def resp_write  = "b10".U

  def isWriteReq(cmd: UInt) = cmd === req_write
}

class SimpleBusInstrReq extends Bundle {
  val addr = UInt(Config.AddrBits.W)
}

class SimpleBusInstrResp extends Bundle {
  val data = UInt(Config.XLEN.W)
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

class SimpleBus extends Bundle {
  val imem = new InstrFetchBus
  val dmem = new AccessMemBus
  // override def cloneType: SimpleBus.this.type = 
  //   new SimpleBus(num_read, num_write).asInstanceOf[this.type]
}