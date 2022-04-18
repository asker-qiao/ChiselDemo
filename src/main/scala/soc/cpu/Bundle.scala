package cpu

import chisel3._
import chisel3.util._

class RedirectIO extends Bundle {
  val target = UInt(Config.AddrBits.W)
}

class DecodeCtrlSignal() extends Bundle {
  val pc          = UInt(32.W)
  val instr       = UInt(32.W)
  val instr_type  = InstrType()
  val op1_data    = UInt(Config.XLEN.W)
  val op2_data    = UInt(Config.XLEN.W)
  val rs2_data    = UInt(Config.XLEN.W)
  val fu_type     = FuType()
  val fu_func     = FuOpType()
  val mem_en      = UInt(1.W)  // access mem enable
  val mem_op      = MemOpType()
  val rf_wen      = Bool()
  val wb_addr     = UInt(5.W)
}

class ExuOutput extends Bundle {
  val pc          = UInt(32.W)
  val instr       = UInt(32.W)
  val instr_type  = InstrType()
  val rs2_data    = UInt(Config.XLEN.W)
  val exe_result  = UInt(Config.XLEN.W)
  val mem_en      = UInt(1.W)  // access mem enable
  val mem_op      = MemOpType()
  val rf_wen      = Bool()
  val wb_addr     = UInt(5.W)
}

class WriteBackIO extends Bundle {
  val pc          = UInt(32.W)
  val instr       = UInt(32.W)
  val instr_type  = InstrType()
  val rf_wen      = Bool()
  val wb_addr     = UInt(5.W)
  val wb_data     = UInt(Config.XLEN.W)
}


class ByPassIO extends Bundle {
  val rf_wen  = Bool()
  val wb_addr = UInt(5.W)
  val wb_data = UInt(Config.XLEN.W)

  def apply(rf_wen: Bool, wb_addr: UInt, wb_data: UInt) = {
    this.rf_wen := rf_wen
    this.wb_addr := wb_addr
    this.wb_data := wb_data
    this
  }

}