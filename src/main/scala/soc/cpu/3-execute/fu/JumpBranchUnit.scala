package cpu

import chisel3._
import chisel3.util._

class JumpBranchUnit extends FunctionUnit(hasRedirect = true) { 

  val (src1, src2, func) = (io.in.bits.src1, io.in.bits.src2, io.in.bits.func)

  io.in.ready := true.B
  val taken = WireInit(false.B)
  
  val offset = src2
  val pc = src1
  val target = pc + offset
  val snpc = pc + 4.U
  
  // for jal jalr
  val isJump = JBUType.isJump(func)
  
  // TODO: for branch
  val isBranch = JBUType.isBranch(func)
  val mis_predict = WireInit(false.B)

  taken := isJump || mis_predict


  io.out.valid := io.in.valid
  io.out.bits.data := snpc

  io.redirect.valid := io.in.valid && taken
  io.redirect.bits.target := target

}