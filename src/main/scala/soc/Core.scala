package soc

import bus._
import chisel3._
import chisel3.util._
import config._
import soc.cache._
import soc.cpu._
import soc.mmu._

class RiscvCore extends Module {
  val io = IO(new Bundle {
    val mem = new DoubleSimpleBus
  })

  val cpu = Module(new SingleCycleCPU)


  // 
  io.mem.imem <> cpu.io.imem
  io.mem.dmem <> cpu.io.dmem
}

class RiscvCore1 extends Module {
  val io = IO(new Bundle {
    val mem = new DoubleSimpleBus
  })

  val cpu     = Module(new FiveStageCPU)
  val icache  = Module(new L1Cache)
  val dcache  = Module(new L1Cache)
  val uncache = Module(new UnCache)

  icache.io.cpu <> cpu.io.imem
  dcache.io.cpu <> cpu.io.dmem

  uncache.io.cpu(0) <> dcache.io.uncache
  uncache.io.cpu(1) <> icache.io.uncache

  io.mem.imem <> icache.io.mem
  io.mem.dmem <> icache.io.mem
}

class RiscvCore2 extends Module {
  val io = IO(new Bundle {
    val mem = new Bundle() {
      val imem = new AXI4
      val dmem = new AXI4
      val uncache = new AXI4
    }
  })

  val cpu     = Module(new FiveStageCPU)
  val icache  = Module(new L1Cache)
  val dcache  = Module(new L1Cache)
  val uncache = Module(new UnCache)
  val itlb    = Module(new TLB(cfg = Config.iTlb))
  val dtlb    = Module(new TLB(cfg = Config.dTlb))
  val ptw     = Module(new PTW)

  icache.io.cpu <> cpu.io.imem

  val dCacheReqArb  = Module(new Arbiter(new PTWReq, 2))
  dCacheReqArb.io.in(0) <> ptw.io.mem
  dCacheReqArb.io.in(1) <> cpu.io.dmem
  dcache.io.cpu <> dCacheReqArb.io.out

  uncache.io.cpu(0) <> dcache.io.uncache
  uncache.io.cpu(1) <> icache.io.uncache

  dtlb.io.cpu <> dcache.io.tlb
  itlb.io.cpu <> icache.io.tlb

  ptw.io.tlb(0) <> dtlb.io.ptw
  ptw.io.tlb(1) <> itlb.io.ptw

  io.mem.imem <> icache.io.mem
  io.mem.dmem <> dcache.io.mem
  io.mem.uncache <> uncache.io.mem
}