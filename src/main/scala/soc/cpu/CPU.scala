package soc.cpu

import chisel3._
import chisel3.util._
import difftest._
import bus._
import utils.PipelineConnectWithValidReg
import config.Config._

class SingleCycleCPU extends Module {
  val io = IO(new Bundle() {
    val imem = new MasterCpuLinkBus
    val dmem = new MasterCpuLinkBus
  })

  // five stage module
  val ifu = Module(new IFU)
  val dec = Module(new DecodeUnit)
  val exe = Module(new EXU)
  val mem = Module(new AccessMem)
  val wbu = Module(new WBU)

  val gpr = Module(new Regfile(debug_port = true))
  val csr = Module(new CSR)

  // connect these stages
  // TODO: note now we have not add pipeline
  ifu.io.out <> dec.io.in
  dec.io.out <> exe.io.in
  exe.io.out <> mem.io.in
  mem.io.out <> wbu.io.in

  // RegFile Read and Write
  gpr.io.read <> dec.io.read
  gpr.io.write <> wbu.io.writeback

  // redirect
  ifu.io.redirect <> exe.io.redirect
  ifu.io.next_pc <> wbu.io.next_pc

  // access memory
  io.imem <> ifu.io.imem
  io.dmem <> mem.io.dmem

  // csr
  dec.io.fromCSR <> csr.io.read
  csr.io.wb <> wbu.io.toCSR
  csr.io.redirect <> wbu.io.csrRedirect
  csr.io.intr := DontCare

  // difftest debug
  if (DiffTest) {
    val debug_gpr = gpr.io.debug_gpr
    val difftest_int = Module(new DifftestArchIntRegState)
    difftest_int.io.clock   := clock
    difftest_int.io.coreid  := 0.U
    difftest_int.io.gpr     := debug_gpr

    val difftest_fp = Module(new DifftestArchFpRegState)
    difftest_fp.io.clock  := clock
    difftest_fp.io.coreid := 0.U
    difftest_fp.io.fpr    := VecInit((0 until 32).map(i => 0.U))
  }

}

class FiveStageCPU extends Module {
  val io = IO(new Bundle() {
    val imem = new MasterCpuLinkBus
    val dmem = new MasterCpuLinkBus
  })

  // five stage module
  val ifu = Module(new IFU)
  val dec = Module(new DecodeUnit)
  val exe = Module(new EXU)
  val mem = Module(new AccessMem)
  val wbu = Module(new WBU)
  val gpr = Module(new Regfile(debug_port = true))

  val flush = wbu.io.next_pc.valid
  // connect these stages using pipeline connect
  PipelineConnectWithValidReg(in = ifu.io.out, out = dec.io.in, in_outFire = dec.io.out.fire, flush = flush)
  PipelineConnectWithValidReg(in = dec.io.out, out = exe.io.in, in_outFire = exe.io.out.fire, flush = flush)
  PipelineConnectWithValidReg(in = exe.io.out, out = mem.io.in, in_outFire = mem.io.out.fire, flush = flush)
  PipelineConnectWithValidReg(in = mem.io.out, out = wbu.io.in, in_outFire = true.B, flush = false.B)

  // RegFile Read and Write
  gpr.io.read <> dec.io.read
  gpr.io.write <> wbu.io.writeback

  // redirect
  ifu.io.redirect <> exe.io.redirect
  ifu.io.next_pc <> wbu.io.next_pc

  // access memory
  io.imem <> ifu.io.imem
  io.dmem <> mem.io.dmem

  // difftest debug
  if (DiffTest) {
    val debug_gpr = gpr.io.debug_gpr
    val difftest_int = Module(new DifftestArchIntRegState)
    difftest_int.io.clock   := clock
    difftest_int.io.coreid  := 0.U
    difftest_int.io.gpr     := debug_gpr

    val difftest_fp = Module(new DifftestArchFpRegState)
    difftest_fp.io.clock  := clock
    difftest_fp.io.coreid := 0.U
    difftest_fp.io.fpr    := VecInit((0 until 32).map(i => 0.U))
  }

}