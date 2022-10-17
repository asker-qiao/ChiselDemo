package soc

import chisel3._
import bus._
import config.Config

class SoC extends Module {
  val io = IO(new Bundle () {
    val mem = new DoubleSimpleBus
  })

  val core = Module(new RiscvCore)

  io.mem <> core.io.mem
}

class SoC1 extends Module {
  val io = IO(new Bundle () {
    val mem = new AXI4
  })

  val core = Module(new RiscvCore2)
  val clint = Module(new CLINT(sim = false))
  val axi_xbar = Module(new CrossBarNto1(io_type = new AXI4, numIn = 3))

  val uncache_xbar = Module(new CrossBar1toN(io_type = new AXI4, addrSpace = Config.non_cacheableAddrSpace))

  uncache_xbar.io.in <> core.io.mem.uncache
  uncache_xbar.io.out(1) <> clint.io.in

  axi_xbar.io.in(0) <> core.io.mem.dmem
  axi_xbar.io.in(1) <> uncache_xbar.io.out(0)
  axi_xbar.io.in(2) <> core.io.mem.imem

  io.mem <> axi_xbar.io.out

}