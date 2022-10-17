package simulator

import chisel3._
import difftest._
import soc._
import bus._
import device._
import soc.cpu.Config

case class SimulatorConfig 
(
  memory_type: String = "2r1w",
  memory_size: Int = 256 * 1024 * 1024,
  beatBytes: Int = 8
)

trait SimulatorConst {
  val cfg = Config.simConfig

  val memory_size: Int = cfg.memory_size
  val beatBytes: Int = cfg.beatBytes
}

class SimTop extends Module {
  val io = IO(new Bundle(){
    val logCtrl = new LogCtrlIO
    val perfInfo = new PerfInfoIO
    val uart = new UARTIO
  })

  val soc = Module(new SoC(io_type = new DoubleSimpleBus))
  val memory = Module(new RAM(io_type = new DoubleSimpleBus()))
  val device = Module(new DeviceTop(io_type = new DoubleSimpleBus))

  soc.io.mem <> memory.io.in
  io.uart <> device.io.uart
}

object GenVerilog extends App {
  println("Generating the verilog file...")
  (new chisel3.stage.ChiselStage).emitVerilog(new SimTop(), args)
  println("End up, Thank you for using the simuator")
}

