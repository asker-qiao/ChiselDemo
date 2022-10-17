package simulator.device

import chisel3._
import difftest.UARTIO
import bus._
import utils.{MaskExpand, RegMap}

class UART[T <: Data](_type: T = new AXI4) extends BaseDevice(io_type = _type) {
  val uartio = IO(new UARTIO)

  val rxfifo = RegInit(0.U(32.W))
  val txfifo = Reg(UInt(32.W))
  val stat = RegInit(1.U(32.W))
  val ctrl = RegInit(0.U(32.W))

  uartio.out.valid := (waddr(3,0) === 4.U && wen)
  uartio.out.ch := wdata(7,0)
  uartio.in.valid := (raddr(3,0) === 0.U && ren)

  val mapping = Seq(
    RegMap(0x4, txfifo),
    RegMap(0x8, stat),
    RegMap(0xc, ctrl)
  )

  RegMap.generate(mapping, raddr(3,0), d_rdata,
    waddr(3,0), wen, wdata, MaskExpand(wstrb >> waddr(2,0))
  )
}
