package bus

import chisel3._
import chisel3.util._
import cpu._

class SimpleReadAddr extends Bundle {
  val addr = UInt(Config.AddrBits.W)
}

class SimpleReadData extends Bundle {
  val data = UInt(Config.XLEN.W)
}

class SimpleWriteAddrData extends Bundle {
  val addr = UInt(Config.AddrBits.W)
  val data = UInt(Config.XLEN.W)
  val strb = UInt((Config.XLEN / 8).W)
  val wen  = Bool()
}

class SimpleBus2r1w extends Bundle {
  val ra = Vec(2, Decoupled(new SimpleReadAddr))
  val rd = Vec(2, Flipped(DecoupledIO(new SimpleReadData)))
  val wd = Decoupled(new SimpleWriteAddrData)

  // override def cloneType: SimpleBus.this.type = 
  //   new SimpleBus(num_read, num_write).asInstanceOf[this.type]
}