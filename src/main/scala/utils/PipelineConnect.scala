package utils

import chisel3._
import chisel3.util._

object PipelineConnect {
  def apply[T <: Data](out: DecoupledIO[T], in: DecoupledIO[T], update: Bool, flush: Bool) = {
    val pipelineRegs = RegInit(0.U.asTypeOf(out.bits))

    in.bits := pipelineRegs
    in.valid := out.valid
    out.ready := in.ready

    when(flush) {
      pipelineRegs := 0.U.asTypeOf(out.bits)
    }.elsewhen(update) {
      pipelineRegs := out.bits
    }
  }
}

object PipelineConnectWithValidReg {
  def apply[T <: Data](out: DecoupledIO[T], in: DecoupledIO[T], in_outFire: Bool, flush: Bool) = {
    val reg_valid = RegInit(false.B)
    val reg_pipeline = RegInit(0.U.asTypeOf(out.bits))

    val pipelineFire = out.valid && in.ready
    when (in_outFire) {
      reg_valid := false.B
      reg_pipeline := 0.U.asTypeOf(out.bits)
    }
    when (pipelineFire) {
      reg_valid := true.B
      reg_pipeline := out.bits
    }
    when (flush) {
      reg_valid := false.B
      reg_pipeline := 0.U.asTypeOf(out.bits)
    }

    out.ready := in.ready
    in.valid := reg_valid
    in.bits := reg_pipeline
  }
}