package soc.cpu

import chisel3._
import chisel3.util._

trait PcOffsetIO {
  this: FunctionUnit => 
  val br_io = IO(new Bundle {
    val pc = Input(UInt(Config.AddrBits.W))
    val rs2_data = Input(UInt(Config.XLEN.W))
  })
}

class JumpBranchUnit extends FunctionUnit(hasRedirect = true) with PcOffsetIO { 

  val (src1, src2, func) = (io.in.bits.src1, io.in.bits.src2, io.in.bits.func)

  io.in.ready := true.B
  val taken = WireInit(false.B)
  
  val base_addr = src1
  val offset    = src2

  val jmp_target = base_addr + offset
  val snpc = base_addr + 4.U
  
  // for jal jalr
  val isJump = JBUType.isJump(func)
  
  // TODO: for branch
  val isBranch = JBUType.isBranch(func)
  val br_taken = WireInit(false.B)

  val rs1 = src1
  val rs2 = br_io.rs2_data
  val br_target = br_io.pc + offset


  taken := isJump || (isBranch && br_taken)


  io.out.valid := io.in.valid
  io.out.bits.data := snpc

  io.redirect.valid := io.in.valid && taken
  io.redirect.bits.target := Mux(isJump, jmp_target, br_target)

}