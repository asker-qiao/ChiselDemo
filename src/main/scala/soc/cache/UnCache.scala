package soc.cache

import chisel3._
import chisel3.util._
import bus._
import main.scala.bus.AXI4

class UnCache extends Module {
  val io = IO(new Bundle() {
    val cpu = Flipped(new MasterSimpleBus)
    val mem = new AXI4
  })


}
