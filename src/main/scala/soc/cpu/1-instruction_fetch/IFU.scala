package soc.cpu

import chisel3._
import chisel3.util._
import bus._
import config.Config._

class FetchInstrInfo extends Bundle {
  val instr = UInt(32.W)
  val pc = UInt(XLEN.W)
  val exception = Vec(ExceptionCode.total, Bool())
}

/**
 * Instruction Fetch Unit
 * This module is responsible for fetching instructions and sending them to the decode unit
 */
class IFU(isPipeLine: Boolean = false) extends Module {
  val io = IO(new Bundle() {
    val out = DecoupledIO(new FetchInstrInfo) // out to next-stage, decode
    val update_pc = Flipped(ValidIO(new UpdatePC))  // update the PC register
    val imem = new MasterCpuLinkBus
  })

  // PC Register
  val pc = RegInit(ResetPC.U(XLEN.W))

  if (!isPipeLine) {  // Single Stage Processor
    val next_pc = io.update_pc.bits.target
    // update pc
    when (io.update_pc.valid) {
      pc := next_pc
    }
  } else {  // Five Stage Processor
    // the BPU for branch prediction
    val bpu = Module(new BPU)
    bpu.io.in.valid := true.B
    bpu.io.in.bits.pc := pc
    val predicted_pc = bpu.io.out.bits.preTarget

    val next_pc = Mux(io.update_pc.valid, io.update_pc.bits.target, predicted_pc)

    // update pc
    when (io.update_pc.valid || io.imem.req.fire) {
      pc := next_pc
    }
  }

  // request to memory for fetching instruction
  io.imem.req.valid := !reset.asBool
  io.imem.req.bits.addr := pc // pc is address of instruction
  io.imem.req.bits.id := CPUBusReqType.instr
  io.imem.req.bits.size := "b10".U
  io.imem.req.bits.cmd := CpuLinkCmd.req_read
  io.imem.req.bits.wdata := DontCare
  io.imem.req.bits.strb := DontCare

  io.imem.resp.ready := true.B

  // TODO: now just support 32 bits length of instruction
  val instr = Mux(pc(2).asBool, io.imem.resp.bits.data(XLEN - 1, 32), io.imem.resp.bits.data(31, 0))
  
  io.out.valid := io.imem.resp.valid
  io.out.bits.pc := pc
  io.out.bits.instr := instr
  io.out.bits.exception := io.imem.resp.bits.exception
}
