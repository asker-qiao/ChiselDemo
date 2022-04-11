package utils

import chisel3._
import chisel3.util._
import yycore._

object RegMap {

  def fullMask = (~(0.U(Parameters.XLEN.W))).asUInt()

  def apply(addr: UInt, reg: UInt, wfn: UInt => UInt = (x => x)): (UInt, UInt, UInt => UInt) = {
    (addr, reg, wfn)
  }

  def generate (mapping: Seq[(UInt, UInt, UInt => UInt)], raddr: UInt, rdata : UInt,
                waddr: UInt, wen: Bool, wdata: UInt, wmask: UInt = fullMask) = {
    // write
    mapping.map { case (a, r, wfn) =>
      if (wfn != null) when(wen && waddr === a) { r := MaskData(r, wdata, wmask)  }
    }
    // read
    rdata := MuxLookup(raddr, 0.U, mapping.map{ case(a, r, wfn) => (a === raddr) -> r })
  }


}
