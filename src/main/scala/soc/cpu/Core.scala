package soc.cpu

import chisel3._
import chisel3.util._
import bus._
import main.scala.bus.AXI4
import soc.cache._
import soc.mmu._

class RiscvCore extends Module {
  val io = IO(new Bundle {
    val mem = new DoubleSimpleBus
  })

  val cpu = Module(new StageFiveCPU)


  // 
  io.mem.imem <> cpu.io.imem
  io.mem.dmem <> cpu.io.dmem
}

class RiscvCore1 extends Module {
  val io = IO(new Bundle {
    val mem = new DoubleSimpleBus
  })

  val cpu     = Module(new StageFiveCPU)
  val icache  = Module(new L1Cache)
  val dcache  = Module(new L1Cache)
  val uncache = Module(new UnCache)

  val crossbar = Module(new SimpleBusCrossBar1toN(addrSpace = Config.cacheAddrSpace))

  crossbar.io.in <> cpu.io.dmem

  icache.io.cpu <> cpu.io.imem
  dcache.io.cpu <> crossbar.io.out(1)
  uncache.io.cpu <> crossbar.io.out(0)

  //
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

  val cpu     = Module(new StageFiveCPU)
  val icache  = Module(new L1Cache)
  val dcache  = Module(new L1Cache)
  val uncache = Module(new UnCache)
  val itlb    = Module(new TLB(cfg = Config.iTlb))
  val dtlb    = Module(new TLB(cfg = Config.dTlb))
  val ptw     = Module(new PTW)

  val crossbar = Module(new SimpleBusCrossBar1toN(addrSpace = Config.cacheAddrSpace))

  val dCacheReqArb  = Module(new Arbiter(new PTWReq, 2))
  val tlbReqArb     = Module(new Arbiter(new PTWReq, 2))

  crossbar.io.in <> cpu.io.dmem

  dCacheReqArb.io.in(0) <> ptw.io.mem
  dCacheReqArb.io.in(1) <> crossbar.io.out(1)
  uncache.io.cpu <> crossbar.io.out(0)

  dcache.io.cpu <> dCacheReqArb.io.out

  tlbReqArb.io.in(0) <> dtlb.io.ptw.req
  tlbReqArb.io.in(1) <> itlb.io.ptw.req
  ptw.io.tlb.req <> tlbReqArb.io.out

  io.mem.imem <> icache.io.mem
  io.mem.dmem <> dcache.io.mem
  io.mem.uncache <> uncache.io.mem
}