package soc.cpu

import chisel3._

trait CSRConst {
  val XLEN = 64
}

class MstatusBundle extends Bundle with CSRConst {
  val sd = UInt(1.W)
  val wpri4  = if (XLEN == 64) UInt(25.W) else null
  val mbe   = if (XLEN == 64) UInt(1.W) else null
  val sbe   = if (XLEN == 64) UInt(1.W) else null
  val sxl   = if (XLEN == 64) UInt(2.W)  else null
  val uxl   = if (XLEN == 64) UInt(2.W)  else null
  val wpri3 = if (XLEN == 64) UInt(9.W)  else UInt(8.W)
  val tsr   = UInt(1.W)
  val tw    = UInt(1.W)
  val tvm   = UInt(1.W)
  val mxr   = UInt(1.W)
  val sum   = UInt(1.W)
  val mprv  = UInt(1.W)
  val xs    = UInt(2.W)
  val fs    = UInt(2.W)
  val mpp   = UInt(2.W)
  val vs   = UInt(2.W)
  val spp   = UInt(1.W)
  val mpie = UInt(1.W)
  val ube = UInt(1.W)
  val spie = UInt(1.W)
  val wpri2 = UInt(1.W)
  val mie = UInt(1.W)
  val wpri1 = UInt(1.W)
  val sie = UInt(1.W)
  val wpri0 = UInt(1.W)

  assert(this.getWidth == XLEN)
}

class SatpBundle extends Bundle {
  val changed = Bool()
  val mode = UInt(4.W)
  val asid = UInt(16.W)
  val zero = UInt(2.W)
  val ppn = UInt(44.W)

  def apply(satp: UInt): Unit = {
    require(satp.getWidth == Config.XLEN)
    val s = satp.asTypeOf(new SatpBundle)
    mode := s.mode
    asid := s.asid
    ppn := s.ppn
  }
}

class CSRtoMMUBundle extends Bundle {

}

class CSR extends FunctionUnit(hasRedirect = true) {
  val XLEN = 64

  val (valid, src1, src2, func) = (io.in.valid, io.in.bits.src1, io.in.bits.src2, io.in.bits.func)

  /**
   * M-mode CSR
   */
  val mstatus = RegInit("ha00000000".U(XLEN.W))
  val mtvec   = RegInit(UInt(XLEN.W), 0.U)
  val mcause  = RegInit(UInt(XLEN.W), 0.U)
  val mtval   = RegInit(UInt(XLEN.W), 0.U)
  val mepc    = Reg(UInt(XLEN.W))

  /**
   * S-mode CSR
   */
}
