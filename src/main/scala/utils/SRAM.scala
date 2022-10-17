package utils

import chisel3._
import chisel3.util._

/**
 * 一生一芯3期 soc 仅提供单口ram模块，此模块用来模拟一生一芯SoC提供的单口ram
 * CEN  | WEN  | Operation |
 * Low  | High | Read      |
 * Low  | Low  | Write     |
 * High | X    | Standby   |
 */
class S011HD1P_X32Y2D128 extends BlackBox {
  val io = IO(new Bundle() {
    val Q = Output(UInt(128.W))
    val CLK = Input(Clock())
    val CEN = Input(Bool())
    val WEN = Input(Bool())
    val A = Input(UInt(6.W))
    val D = Input(UInt(128.W))
  })
}

/**
 *
 * @param gen
 * @param set
 * @tparam T
 */
class SRAMSinglePort[T<:Data](val gen: T, set: Int, useBlackBox: Boolean = false) extends Module {
  val io = IO(new Bundle() {
    val wen = Input(Bool()) // write enable signal
    val addr = Input(UInt(6.W))
    val wdata = Input(UInt(128.W))
    val rdata = Output(UInt(128.W))
  })

  val rdata = if(useBlackBox) {
    val ram = Module(new S011HD1P_X32Y2D128())
    ram.io.CLK := clock
    ram.io.CEN := false.B
    ram.io.WEN := !io.wen
    ram.io.A := io.addr
    ram.io.D := io.wdata
    ram.io.Q
  } else {
    val wordType = UInt(gen.getWidth.W)
    // sram array
    val ram = SyncReadMem(set, wordType)

    val readEn =  !io.wen
    val writeEn = io.wen

    //io.rdata := ram.read(io.addr, readEn)
    when(writeEn){
      ram.write(io.addr, io.wdata)
    }
    ram.read(io.addr, readEn)
  }

  io.rdata := rdata

}
