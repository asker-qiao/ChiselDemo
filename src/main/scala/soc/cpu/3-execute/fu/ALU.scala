package cpu

import chisel3._
import chisel3.util._

object ALUOpType {
  def fuOpType = 4
  // ALU Operation Signal
  val X   = 0.asUInt(fuOpType.W)
  val ADD = 0.asUInt(fuOpType.W)
  val SUB = 1.asUInt(fuOpType.W)
  val SLL = 2.asUInt(fuOpType.W)
  val SRL = 3.asUInt(fuOpType.W)
  val SRA = 4.asUInt(fuOpType.W)
  val AND = 5.asUInt(fuOpType.W)
  val OR  = 6.asUInt(fuOpType.W)
  val XOR = 7.asUInt(fuOpType.W)
  val SLT = 8.asUInt(fuOpType.W)
  val SLTU= 9.asUInt(fuOpType.W)

  val ADDW = 10.asUInt(fuOpType.W)
  val SUBW = 11.asUInt(fuOpType.W)
  val SLLW = 12.asUInt(fuOpType.W)
  val SRLW = 13.asUInt(fuOpType.W)
  val SRAW = 14.asUInt(fuOpType.W)
  val COPY1= 15.asUInt(fuOpType.W)
}

class ALU extends FunctionUnit(hasRedirect = false) {
  val (src1, src2, func) = (io.in.bits.src1, io.in.bits.src2, io.in.bits.func)

  io.in.ready := true.B

  // alu operation
  val alu_out = MuxCase(0.U, Array(
    (func === ALUOpType.ADD)    ->  (src1 + src2).asUInt(),
    (func === ALUOpType.SUB)    ->  (src1 - src2).asUInt(),
    (func === ALUOpType.AND)    ->  (src1 & src2).asUInt(),
  ))

  io.out.valid := io.in.valid
  io.out.bits.data := alu_out
}