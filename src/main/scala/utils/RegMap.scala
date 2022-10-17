package utils

import chisel3._
import chisel3.util._
import config.Config._


object RegMap {

  def fullMask = (~(0.U(XLEN.W))).asUInt()

  def apply(addr: Int, reg: UInt, wfn: UInt => UInt = (x => x)): (Int, UInt, UInt => UInt) = {
    (addr, reg, wfn)
  }

  def generate (mapping: Seq[(Int, UInt, UInt => UInt)], raddr: UInt, rdata : UInt,
                waddr: UInt, wen: Bool, wdata: UInt, wmask: UInt = fullMask) = {
    // write
    mapping.map { case (a, r, wfn) =>
      if (wfn != null) when(wen && waddr === a.U) { r := MaskData(r, wdata, wmask)  }
    }
    // read
    rdata := MuxLookup(raddr, 0.U, mapping.map{ case(a, r, wfn) => (a.U === raddr) -> r })
  }


}
