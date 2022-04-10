package demo

import chisel3._
import chisel3.util._

/**
 * This is a very basic ALU example.
 * taken from https://github.com/schoeberl/chisel-examples/blob/master/src/main/scala/simple/Alu.scala
 */
class Alu extends Module {
  val io = IO(new Bundle {
    val fn = Input(UInt(2.W))
    val a = Input(UInt(4.W))
    val b = Input(UInt(4.W))
    val result = Output(UInt(4.W))
  })

  // Use shorter variable names
  val fn = io.fn
  val a = io.a
  val b = io.b

  val result = Wire(UInt(4.W))
  // some default value is needed
  result := 0.U

  // The ALU selection
  switch(fn) {
    is(0.U) { result := a + b }
    is(1.U) { result := a - b }
    is(2.U) { result := a | b }
    is(3.U) { result := a & b }
  }

  // Output the alu result
  io.result := result
}

class SRAMTest extends Module {
  val io = IO(new Bundle() {
    val r_addr = Input(UInt(5.W))
    val r_data = Output(UInt(32.W))
  })

  val array = SyncReadMem(32, UInt(64.W))

  io.r_data := array.read(io.r_addr)
}

// Generate the Verilog code
object AluMain extends App {
  println("Generating the ALU hardware")
  (new chisel3.stage.ChiselStage).emitVerilog(new SRAMTest(), Array("--target-dir", "build"))
}