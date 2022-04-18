package cpu

import chisel3._
import chisel3.util._

object ALUOpType {
  // ALU Operation Signal
  val X   = "b0000".U
  val ADD = "b0000".U
}

class ALU extends FunctionUnit(hasRedirect = false) {
  val (src1, src2, func) = (io.in.bits.src1, io.in.bits.src2, io.in.bits.func)

  io.in.ready := true.B

  // alu operation
  val alu_out = MuxCase(0.U, Array(
    (func === ALUOpType.ADD)    ->  (src1 + src2).asUInt(),
  ))

  io.out.valid := io.in.valid
  io.out.bits.data := alu_out
}