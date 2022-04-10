package demo

import chisel3._
import chisel3.util._

class Counter(max: Int = 16) extends Module {
  // scala value
  val width = if (isPow2(max)) log2Up(max) + 1 else log2Up(max)

  // io: interface of the circuit module 
  val io = IO(new Bundle() {
    val out = Output(UInt(width.W))
  })

  // chisel object to present a circuit, there are a Register with 0 reset signals value
  val count = RegInit(0.U(width.W))

  count := count + 1.U

  io.out := count
}
