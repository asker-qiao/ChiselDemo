package soc.cpu

import chisel3._
import chisel3.util._

class RegfileReadIO extends Bundle {
  val addr = Input(UInt(5.W))
  val data = Output(UInt(Config.XLEN.W))
}

class RegfileWriteIO extends Bundle {
  val addr = Input(UInt(5.W))
  val data = Input(UInt(Config.XLEN.W))
  val wen = Input(Bool())
}

class Regfile
(
  debug_port: Boolean
) extends Module {
  val io = IO(new Bundle() {
    val read = Vec(2, new RegfileReadIO)
    val write = new RegfileWriteIO
    val debug_gpr = if (debug_port) Output(Vec(32, UInt(Config.XLEN.W))) else null
  })

  val regfiles = RegInit(VecInit(Seq.fill(32)(0.U(Config.XLEN.W))))

  // read
  for (i <- 0 until 2) {
    io.read(i).data := Mux(io.read(i).addr =/= 0.U, regfiles(io.read(i).addr), 0.U)
  }

  // write
  when (io.write.addr =/= 0.U && io.write.wen) {
    regfiles(io.write.addr) := io.write.data
  }

  if (debug_port) {
    io.debug_gpr.zipWithIndex.map{ case (d, i) => d := regfiles(i) }
  }

}