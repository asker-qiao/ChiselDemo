package cpu

import chisel3._
import chisel3.util._
import difftest._

/**
  * Write Back Unit
  *
  */
class WBU extends Module {
  val io = IO(new Bundle() {
    val in = Flipped(DecoupledIO(new WriteBackIO))
    val writeback = Flipped(new RegfileWriteIO)
    val next_pc = ValidIO(new RedirectIO)
  })

  io.in.ready := true.B
  
  io.writeback.addr := io.in.bits.wb_addr
  io.writeback.data := io.in.bits.wb_data
  io.writeback.wen  := io.in.valid && io.in.bits.rf_wen

  io.next_pc.valid        := io.in.valid
  io.next_pc.bits.target  := io.in.bits.pc + 4.U

  val difftest_instr  = Module(new DifftestInstrCommit)
  difftest_instr.io.clock     := clock
  difftest_instr.io.coreid    := 0.U
  difftest_instr.io.index     := 0.U
  difftest_instr.io.instr     := RegNext(io.in.bits.instr)
  difftest_instr.io.dec_type  := RegNext(io.in.bits.instr_type)
  difftest_instr.io.valid     := RegNext(io.in.valid) && !reset.asBool
  difftest_instr.io.pc        := RegNext(io.in.bits.pc)
  difftest_instr.io.special   := 0.U
  difftest_instr.io.skip      := false.B  // TODO: RegNext(isMMIO)
  difftest_instr.io.isRVC     := false.B
  difftest_instr.io.wen       := RegNext(io.in.valid && io.in.bits.rf_wen && io.in.bits.wb_addr =/= 0.U)
  difftest_instr.io.wpdest    := 0.U      // useless 
  difftest_instr.io.wdest     := RegNext(io.in.bits.wb_addr)
  difftest_instr.io.wdata     := RegNext(io.in.bits.wb_data)

  val instrCnt = RegInit(0.U(64.W))
  val cycleCnt = RegInit(0.U(64.W))
  when (!reset.asBool) {
    cycleCnt := cycleCnt + 1.U
  }
  
  when (io.in.fire) {
    instrCnt := instrCnt + 1.U
  }
  val hitTrap = io.in.valid && io.in.bits.instr === SelfDefineTrap.TRAP
  val trapCode = io.in.bits.wb_data
  val trapPC = io.in.bits.pc

  val difftest_trap = Module(new DifftestTrapEvent)
  difftest_trap.io.clock    := clock
  difftest_trap.io.coreid   := 0.U
  difftest_trap.io.valid    := hitTrap
  difftest_trap.io.code     := trapCode
  difftest_trap.io.pc       := trapPC
  difftest_trap.io.cycleCnt := cycleCnt
  difftest_trap.io.instrCnt := instrCnt

}