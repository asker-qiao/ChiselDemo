package simulator.device

import bus._
import chisel3._
import chisel3.experimental.ExtModule
import chisel3.util._
import utils._
import config._

class RAMHelper_2r1w(memByte: BigInt) extends ExtModule {
  val DataBits = 64

  val clk     = IO(Input(Clock()))
  val en      = IO(Input(Bool()))
  val rIdx_0  = IO(Input(UInt(DataBits.W)))
  val rdata_0 = IO(Output(UInt(DataBits.W)))
  val rIdx_1  = IO(Input(UInt(DataBits.W)))
  val rdata_1 = IO(Output(UInt(DataBits.W)))
  val wIdx    = IO(Input(UInt(DataBits.W)))
  val wdata   = IO(Input(UInt(DataBits.W)))
  val wmask   = IO(Input(UInt(DataBits.W)))
  val wen     = IO(Input(Bool()))
}

class RAMHelper(memByte: BigInt) extends ExtModule {
  val DataBits = 64

  val clk     = IO(Input(Clock()))
  val en      = IO(Input(Bool()))
  val rIdx  = IO(Input(UInt(DataBits.W)))
  val rdata = IO(Output(UInt(DataBits.W)))
  val wIdx    = IO(Input(UInt(DataBits.W)))
  val wdata   = IO(Input(UInt(DataBits.W)))
  val wmask   = IO(Input(UInt(DataBits.W)))
  val wen     = IO(Input(Bool()))
}

class RAM[T <: Data]
(
  io_type: T = null,
  memByte: Long = 256 * 1024 * 1024,
  beatBytes: Int = 8
) extends Module {
  require(io_type != null, "io must be defined!")

  val io = IO(new Bundle() {
    //val rw = if (io_type == null) null else io_type.cloneType
    val in = Flipped(new DoubleCpuLink())
  })

  io.in.imem.req.ready := true.B
  io.in.imem.resp.valid := io.in.imem.req.valid

  io.in.dmem.req.ready := true.B
  io.in.dmem.resp.valid := io.in.dmem.req.valid

  val offsetBits = log2Up(memByte)
  val offsetMask = (1 << offsetBits) - 1
  val split = beatBytes / 8
  val bankByte = memByte / split

  def index(addr: UInt) = ((addr & offsetMask.U) >> log2Ceil(beatBytes)).asUInt()
  def inRange(idx: UInt) = idx < (memByte / 8).U

  val rIdx  = Wire(Vec(2, UInt()))
  val rdata = Wire(Vec(2, UInt()))
  val wIdx  = Wire(UInt())
  val wdata = Wire(UInt())
  val wstrb = Wire(UInt())
  val wen   = Wire(Bool())

  // read event
  rIdx(0) := index(io.in.imem.req.bits.addr)
  rIdx(1) := index(io.in.dmem.req.bits.addr)
  io.in.imem.resp.bits.data := rdata(0)
  io.in.dmem.resp.bits.data := rdata(1)

  // write event
  wIdx  := index(io.in.dmem.req.bits.addr)
  wdata := io.in.dmem.req.bits.wdata
  wstrb := io.in.dmem.req.bits.strb
  wen   := CpuLinkCmd.isWriteReq(cmd = io.in.dmem.req.bits.cmd)

  val mems = (0 until split).map {_ => Module(new RAMHelper_2r1w(bankByte))}
  mems.zipWithIndex map { case (mem, i) =>
    mem.clk     := clock
    mem.en      := !reset.asBool()
    mem.rIdx_0  := (rIdx(0) << log2Ceil(split)) + i.U
    mem.rIdx_1  := (rIdx(1) << log2Ceil(split)) + i.U
    mem.wIdx    := (wIdx << log2Ceil(split)) + i.U
    mem.wdata   := wdata((i + 1) * 64 - 1, i * 64)
    mem.wmask   := MaskExpand(wstrb((i + 1) * 8 - 1, i * 8))
    mem.wen     := wen
  }

  val rdata_0 = mems.map {mem => mem.rdata_0}
  val rdata_1 = mems.map {mem => mem.rdata_1}
  rdata(0) := Cat(rdata_0.reverse)
  rdata(1) := Cat(rdata_1.reverse)
}

class MainMemory[T <: Bundle]
(
  _type: T = new AXI4,
  memByte: Long = 256 * 1024 * 1024,
  beatBytes: Int = 8
) extends BaseDevice(io_type = _type) {

  val offsetBits = log2Up(memByte)
  val offsetMask = (1 << offsetBits) - 1
  val split = beatBytes / 8
  val bankByte = memByte / split

  def index(addr: UInt) = ((addr & offsetMask.U) >> log2Ceil(beatBytes)).asUInt()
  def inRange(idx: UInt) = idx < (memByte / 8).U

  val rIdx = index(raddr)
  val wIdx = index(waddr)

  io.in match {
    case in: DoubleCpuLink  =>
      val rIdx_1 = index(raddr2)

      val mems = (0 until split).map {_ => Module(new RAMHelper_2r1w(bankByte))}
      mems.zipWithIndex map { case (mem, i) =>
        mem.clk     := clock
        mem.en      := !reset.asBool()
        mem.rIdx_0  := (rIdx << log2Ceil(split)) + i.U
        mem.rIdx_1  := (rIdx_1 << log2Ceil(split)) + i.U
        mem.wIdx    := (wIdx << log2Ceil(split)) + i.U
        mem.wdata   := wdata((i + 1) * 64 - 1, i * 64)
        mem.wmask   := MaskExpand(wstrb((i + 1) * 8 - 1, i * 8))
        mem.wen     := wen
      }
      val rXLEN_0 = mems.map {mem => mem.rdata_0}
      val rXLEN_1 = mems.map {mem => mem.rdata_1}
      rdata := Cat(rXLEN_0.reverse)
      rdata2 := Cat(rXLEN_1.reverse)
    case axi: AXI4  =>

      val mems = (0 until split).map {_ => Module(new RAMHelper(bankByte))}
      mems.zipWithIndex map { case (mem, i) =>
        mem.clk     := clock
        mem.en      := !reset.asBool()
        mem.rIdx    := (rIdx << log2Ceil(split)) + i.U
        mem.wIdx    := (wIdx << log2Ceil(split)) + i.U
        mem.wdata   := wdata((i + 1) * 64 - 1, i * 64)
        mem.wmask   := MaskExpand(wstrb((i + 1) * 8 - 1, i * 8))
        mem.wen     := wen
      }

      rdata := Cat((mems.map {mem => mem.rdata}).reverse)

  }

}