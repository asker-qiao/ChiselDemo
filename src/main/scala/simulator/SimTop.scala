package simulator

import chisel3._
import difftest._
import soc.SoC
import bus._

case class SimulatorConfig 
(
  memory_type: String = "2r1w",
  memory_size: Int = 256 * 1024 * 1024
)


class SimTop extends Module {
  val io = IO(new Bundle(){
    val logCtrl = new LogCtrlIO
    val perfInfo = new PerfInfoIO
    val uart = new UARTIO
  })
  io.uart := DontCare
  

  val soc = Module(new SoC(io_type = new SimpleBus))
  val memory = Module(new RAM(io_type = new SimpleBus()SimpleBus))

  soc.io.mem <> memory.io.in
}

object GenVerilog extends App {
  println("Generating the verilog file...")
  (new chisel3.stage.ChiselStage).emitVerilog(new SimTop(), args)
  println("End up, Thank you for using the simuator")
}

