package simulator

import chisel3._
import chisel3.util._
import chisel3.experimental.ExtModule
import bus._
import utils._
import cpu._

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

class RAM[T <: Data]
(
  io_type: T = null,
  memByte: Long = 256 * 1024 * 1024,
  beatBytes: Int = 8
) extends Module {
  require(io_type != null, "io must be defined!")

  val io = IO(new Bundle() {
    //val rw = if (io_type == null) null else io_type.cloneType
    val rw = Flipped(new SimpleBus2r1w())
  })

  io.rw.ra.map(_.ready := true.B)
  io.rw.wd.ready := true.B
  io.rw.rd.zip(io.rw.ra).map{ case (d, a) => d.valid := a.valid}

  val offsetBits = log2Up(memByte)
  val offsetMask = (1 << offsetBits) - 1
  val split = beatBytes / 8
  val bankByte = memByte / split

  println("log2Ceil(split)="+log2Ceil(split))

  def index(addr: UInt) = ((addr & offsetMask.U) >> log2Ceil(beatBytes)).asUInt()
  def inRange(idx: UInt) = idx < (memByte / 8).U

  val rIdx  = Wire(Vec(2, UInt()))
  val rdata = Wire(Vec(2, UInt()))
  val wIdx  = Wire(UInt())
  val wdata = Wire(UInt())
  val wstrb = Wire(UInt())
  val wen   = Wire(Bool())

  for (i <- 0 until 2) {
    rIdx(i) := index(io.rw.ra(i).bits.addr)
    //printf("rIdx(%d)=%x, addr=%x\n", i.U, rIdx(i), io.rw.ra(i).bits.addr)
    io.rw.rd(i).bits.data := rdata(i)
  }

  wIdx  := index(io.rw.wd.bits.addr)
  wdata := io.rw.wd.bits.data
  wstrb := io.rw.wd.bits.strb
  wen   := io.rw.wd.bits.wen

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

object MainMemory {
  def apply(mem_config: String) = {
    mem_config match {
      case "2r1w" => Module(new RAM(io_type = new SimpleBus2r1w))
    }
  }
}