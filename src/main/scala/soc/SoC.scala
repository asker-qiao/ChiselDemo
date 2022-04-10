package soc

import chisel3._
import cpu._
import bus._

class SoC[T <: Data]
(
  io_type: T = new SimpleBus2r1w
) extends Module {
  require(io_type != null)

  val io = IO(new Bundle () {
    // val mem = if (io_type == null) null else io_type.cloneType
    val mem = new SimpleBus2r1w
  })

  val core = Module(new RiscvCore)
  // TODO: add L2 Cache and L3 Cache in the furture

  io.mem.ra(0) <> core.io.imem.req
  io.mem.ra(1) <> core.io.dmem.r_req
  core.io.imem.resp <> io.mem.rd(0)
  core.io.dmem.r_resp <> io.mem.rd(1)
  io.mem.wd <> core.io.dmem.w_req


}