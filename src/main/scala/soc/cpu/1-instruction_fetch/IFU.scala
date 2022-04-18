package cpu

import chisel3._
import chisel3.util._
import bus._


class FetchInstrInfo extends Bundle {
  val instr = UInt(32.W)
  val pc = UInt(Config.AddrBits.W)
}

/**
 * Instruction Fetch Unit
 */
class IFU extends Module {
  val io = IO(new Bundle() {
    val out = DecoupledIO(new FetchInstrInfo)
    val redirect = Flipped(ValidIO(new RedirectIO))
    val next_pc = Flipped(ValidIO(new RedirectIO))
    val imem = new InstrFetchBus
  })

  // PC
  val pc = RegInit(Config.ResetPC.U)
  val pc_next = Mux(io.redirect.valid, io.redirect.bits.target, io.next_pc.bits.target)

  // update pc
  when (io.next_pc.valid || io.redirect.valid) {
    pc := pc_next
  }

  // issue req reading instruction
  io.imem.req.valid := !reset.asBool
  io.imem.req.bits.addr := pc

  io.imem.resp.ready := true.B
  // TODO: now just support 32 bits length of instruction
  val instr = Mux(pc(2).asBool, io.imem.resp.bits.data(Config.XLEN - 1, 32), io.imem.resp.bits.data(31, 0))
  
  io.out.valid := io.imem.resp.valid
  io.out.bits.pc := pc
  io.out.bits.instr := instr
}