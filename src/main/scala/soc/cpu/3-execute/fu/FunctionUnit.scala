package soc.cpu

import chisel3._
import chisel3.util._
import config._

class FuInputIO extends Bundle {
  val src1 = UInt(Config.XLEN.W)
  val src2 = UInt(Config.XLEN.W)
  val func = FuOpType()
}

class FuOutputIO extends Bundle {
  val data = UInt(Config.XLEN.W)
}

abstract class FunctionUnit(
  hasRedirect: Boolean = false
) extends Module {
  val io = IO(new Bundle() {
    val in = Flipped(DecoupledIO(new FuInputIO))
    val out = DecoupledIO(new FuOutputIO)
    val redirect = if (hasRedirect) ValidIO(new RedirectIO) else null
  })

}
