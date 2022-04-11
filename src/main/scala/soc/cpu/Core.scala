package cpu

import chisel3._
import chisel3.util._
import bus._

class RiscvCore extends Module {
  val io = IO(new Bundle {
    val mem = new SimpleBus
  })

  val cpu = Module(new StageFiveCPU)
  // TODO: add L1 Cache here in the furture

  // 
  io.mem.imem <> cpu.io.imem
  io.mem.dmem <> cpu.io.dmem
}