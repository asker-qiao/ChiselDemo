package soc

import chisel3._
import cpu._
import bus._

class SoC[T <: Data]
(
  io_type: T = new SimpleBus
) extends Module {
  require(io_type != null)

  val io = IO(new Bundle () {
    // val mem = if (io_type == null) null else io_type.cloneType
    val mem = new SimpleBus
  })

  val core = Module(new RiscvCore)
  // TODO: add L2 Cache and L3 Cache in the furture

  io.mem <> core.io.mem

}