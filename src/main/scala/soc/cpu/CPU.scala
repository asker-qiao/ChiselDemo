package cpu

import chisel3._
import chisel3.util._
import difftest._
import bus._

class StageFiveCPU extends Module {
  val io = IO(new Bundle() {
    val imem = new InstrFetchBus
    val dmem = new AccessMemBus
  })

  // five stage module
  val ifu = Module(new IFU)
  val dec = Module(new DecodeUnit)
  val exe = Module(new EXU)
  val mem = Module(new AccessMem)
  val wbu = Module(new WBU)
  val gpr = Module(new Regfile(debug_port = true))

  // connect these stages
  // TODO: note now we have not add pipeline
  ifu.io.out <> dec.io.in
  dec.io.out <> exe.io.in
  exe.io.out <> mem.io.in
  mem.io.out <> wbu.io.in

  // regfile read and write
  gpr.io.read <> dec.io.read
  gpr.io.write <> wbu.io.writeback

  // redirect
  ifu.io.redirect <> exe.io.redirect
  ifu.io.next_pc <> wbu.io.next_pc

  // access memory
  io.imem <> ifu.io.imem
  io.dmem <> mem.io.dmem

  // difftest debug
  val debug_gpr = gpr.io.debug_gpr
  val difftest_int = Module(new DifftestArchIntRegState)
  difftest_int.io.clock   := clock
  difftest_int.io.coreid  := 0.U
  difftest_int.io.gpr     := debug_gpr

  val difftest_fp = Module(new DifftestArchFpRegState)
  difftest_fp.io.clock  := clock
  difftest_fp.io.coreid := 0.U
  difftest_fp.io.fpr    := VecInit((0 until 32).map(i => 0.U))

  // difftest csr
    val difftest_csr = Module(new DifftestCSRState)
    difftest_csr.suggestName("difftest_csr")
    difftest_csr.io.clock           := clock
    difftest_csr.io.coreid          := 0.U
    difftest_csr.io.priviledgeMode  := "b11".U
    difftest_csr.io.mstatus         := 0.U
    difftest_csr.io.sstatus         := 0.U
    difftest_csr.io.mepc            := 0.U
    difftest_csr.io.sepc            := 0.U
    difftest_csr.io.mtval           := 0.U
    difftest_csr.io.stval           := 0.U
    difftest_csr.io.mtvec           := 0.U
    difftest_csr.io.stvec           := 0.U
    difftest_csr.io.mcause          := 0.U
    difftest_csr.io.scause          := 0.U
    difftest_csr.io.satp            := 0.U
    difftest_csr.io.mip             := 0.U
    difftest_csr.io.mie             := 0.U
    difftest_csr.io.mscratch        := 0.U
    difftest_csr.io.sscratch        := 0.U
    difftest_csr.io.mideleg         := 0.U
    difftest_csr.io.medeleg         := 0.U




}