package soc.cpu

import chisel3._
import chisel3.util._
import config.Config._

class BPU extends Module {
  val io = IO(new Bundle(){
    val in = Flipped(ValidIO(new Bundle {
      val pc = Output(UInt(AddrBits.W))
    }))
    val out = ValidIO(new Bundle() {
      val preTarget = UInt(AddrBits.W)
    })
  })

  io.out.valid := io.in.valid
  io.out.bits.preTarget := io.in.bits.pc + 4.U
}
