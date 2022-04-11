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
  
  
  val sim_config = SimulatorConfig(
    memory_type = "2r1w",
    memory_size = 256 * 1024 * 1024
  )

  def get_bus_type(_type: String) = {
    _type match {
      case "2r1w" => new SimpleBus2r1w()
    }
  }

  val bus_type = get_bus_type(sim_config.memory_type)

  val soc = Module(new SoC(io_type = bus_type))
  val memory = Module(new RAM(io_type = new SimpleBus2r1w()))

  soc.io.mem <> memory.io.in
}

object GenVerilog extends App {
  println("Generating the verilog file...")
  (new chisel3.stage.ChiselStage).emitVerilog(new SimTop(), args)
  println("End up, Thank you for using the simuator")
}

