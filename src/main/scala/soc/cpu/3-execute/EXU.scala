package cpu

import chisel3._
import chisel3.util._


/**
 * Execute Unit
 */
class EXU extends Module {
  val io = IO(new Bundle() {
    val in = Flipped(DecoupledIO(new DecodeCtrlSignal))
    val out = DecoupledIO(new ExuOutput)
    val redirect = ValidIO(new RedirectIO)
  })

  val (op1_data, op2_data, fu_type, fu_func) = (io.in.bits.op1_data,
    io.in.bits.op2_data,
    io.in.bits.fu_type,
    io.in.bits.fu_func)

  io.in.ready := io.out.ready

  val isALU = FuType.isALU(fu_type)
  val isJBU = FuType.isJBU(fu_type)

  val alu = Module(new ALU)
  alu.io.in.valid     := io.in.valid && isALU
  alu.io.in.bits.src1 := op1_data
  alu.io.in.bits.src2 := op2_data
  alu.io.in.bits.func := fu_func
  alu.io.out.ready    := io.out.ready

  val jbu = Module(new JumpBranchUnit)
  jbu.io.in.valid     := io.in.valid && isJBU
  jbu.io.in.bits.func := fu_func
  jbu.io.in.bits.src1 := op1_data
  jbu.io.in.bits.src2 := op2_data
  jbu.br_io.pc        := io.in.bits.pc
  jbu.br_io.rs2_data  := io.in.bits.rs2_data
  jbu.io.out.ready    := io.out.ready

  // TODO: add more fu such as MDU(mul/div unit)、CSR...


  val exe_result = Mux(isALU, alu.io.out.bits.data, jbu.io.out.bits.data)
  
  // redirect
  io.redirect <> jbu.io.redirect

  // output to next stage
  io.out.valid           := io.in.valid
  io.out.bits.pc         := io.in.bits.pc
  io.out.bits.instr      := io.in.bits.instr
  io.out.bits.rs2_data   := io.in.bits.rs2_data
  io.out.bits.exe_result := exe_result
  io.out.bits.mem_en     := io.in.bits.mem_en
  io.out.bits.mem_op     := io.in.bits.mem_op
  io.out.bits.rf_wen     := io.in.bits.rf_wen
  io.out.bits.wb_addr    := io.in.bits.wb_addr

}