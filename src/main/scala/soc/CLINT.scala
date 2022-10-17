package soc

import chisel3._
import chisel3.util._
import bus._
import utils._

class ClintIO extends Bundle() {
  val mtip = Bool()
  val msip = Bool()
}

/*
* 当软件写1至msip寄存器触发软件中断，CSR寄存器mip中的MSIP置高指示当前中断等待（Pending）状态，
* 软件可通过写0至msip寄存器来清除该软件中断
*/
class CLINT(sim: Boolean = false) extends Module {
  val io = IO(new Bundle() {
    val cpu = Flipped(new MasterSimpleBus)
    val toCpu = Output(new ClintIO)
  })

  val raddr = io.cpu.req.bits.addr
  val waddr = io.cpu.req.bits.addr

  val msip = RegInit(0.U(64.W))     // 生成软件中断
  val mtime = RegInit(0.U(64.W))    // 计时器的值
  val mtimecmp = RegInit(0.U(64.W)) // 计时器的比较值

  val clk = (if(!sim) 10 else 1000)
  val freq = RegInit(clk.U(16.W))
  val inc = RegInit(1.U(16.W))

  val cnt = RegInit(0.U(16.W))
  val nextCnt = cnt + 1.U
  cnt := Mux(nextCnt < freq, nextCnt, 0.U)
  val tick = (nextCnt === freq)
  when(tick){
    mtime := mtime + inc
  }

  def getOffset(addr: UInt) = addr(15,0)
  val reg_raddr = raddr(15, 0)
  val reg_wadrr = waddr(15, 0)
  io.r.bits.data := MuxCase(0.U, Array(
    (reg_raddr === 0x0.U) -> msip,
    (reg_raddr === 0x4000.U) -> mtimecmp,
    (reg_raddr === 0x8000.U) -> freq,
    (reg_raddr === 0x8008.U) -> inc,
    (reg_raddr === 0xbff8.U) -> mtime
  ))
  when(io.cpu.req.fire && io.cpu.req.bits.cmd === SimpleBusCmd.req_write){
    when(reg_wadrr === 0x0.U){
      msip := MaskData(msip, in.w.bits.data, MaskExpand(in.w.bits.strb))
    }.elsewhen(reg_wadrr === 0x4000.U){
      mtimecmp := MaskData(mtimecmp, in.w.bits.data, MaskExpand(in.w.bits.strb))
    }.elsewhen(reg_wadrr === 0x8000.U){
      freq := MaskData(freq, in.w.bits.data, MaskExpand(in.w.bits.strb))
    }.elsewhen(reg_wadrr === 0x8008.U){
      inc := MaskData(inc, in.w.bits.data, MaskExpand(in.w.bits.strb))
    }.elsewhen(reg_wadrr === 0xbff8.U){
      mtime := MaskData(mtime, in.w.bits.data, MaskExpand(in.w.bits.strb))
    }.otherwise{
      printf("CLINT: addr ERROR!!!\n")
    }
  }

  io.toCpu.mtip := RegNext(mtime >= mtimecmp)
  io.toCpu.msip := RegNext(msip =/= 0.U)
}