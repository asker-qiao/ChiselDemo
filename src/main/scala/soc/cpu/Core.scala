package cpu

import chisel3._
import chisel3.util._

class RiscvCore extends Module {
  val io = IO(new Bundle {
    val imem = new InstrFetchBus
    val dmem = new AccessMemBus
  })

  val cpu = Module(new StageFiveCPU)
  // TODO: add L1 Cache here in the furture

  // 
  io.imem <> cpu.io.imem
  io.dmem <> cpu.io.dmem
}