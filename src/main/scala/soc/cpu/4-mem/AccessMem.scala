package cpu

import chisel3._
import chisel3.util._
import Config.XLEN
import bus._

object MemOpType {
  // bits(3): 0 load, 1 store
  // bits(2): 0 Signed, 1 Unsigned
  // bit(1, 0): size
  def lb   = "b0000".U
  def lh   = "b0001".U
  def lw   = "b0010".U
  def ld   = "b0011".U
  def lbu  = "b0100".U
  def lhu  = "b0101".U
  def lwu  = "b0110".U
  def sb   = "b1000".U
  def sh   = "b1001".U
  def sw   = "b1010".U
  def sd   = "b1011".U

  def no = "b0000".U
  def apply() = UInt(4.W)
  def isLoad(op: UInt): Bool = !op(3)
  def isStore(op: UInt): Bool = op(3)
  def genSize(op: UInt): UInt = op(1, 0)
  def genMask(addr: UInt, size: UInt) : UInt = {
    val wmask = Wire(Vec(8, Bool()))
    val numValid = 1.U << size
    wmask.zipWithIndex.map{ case (m, i) =>
      m := addr(2, 0) <= i.U && addr(2, 0) + numValid.asUInt() > i.U
    }
    wmask.asUInt()
  }
  def WriteData(addr: UInt, rs2: UInt): UInt = {
    val shiftwdata = Wire(UInt(XLEN.W))
    shiftwdata := MuxLookup(addr(2, 0), rs2, List(
      "b001".U  -> Cat(rs2(XLEN - 9, 0),  Fill(8, "b0".U)),
      "b010".U  -> Cat(rs2(XLEN - 17, 0), Fill(16, "b0".U)),
      "b011".U  -> Cat(rs2(XLEN - 25, 0), Fill(24, "b0".U)),
      "b100".U  -> Cat(rs2(XLEN - 33, 0), Fill(32, "b0".U)),
      "b101".U  -> Cat(rs2(XLEN - 41, 0), Fill(40, "b0".U)),
      "b110".U  -> Cat(rs2(XLEN - 49, 0), Fill(48, "b0".U)),
      "b111".U  -> Cat(rs2(XLEN - 57, 0), Fill(56, "b0".U)),
    ))
    //shiftwdata := wdata << (addr(2, 0) << 3)
    shiftwdata
  }
  def genWdata(addr: UInt, wdata: UInt, size: UInt): UInt = {
    val partdata = MuxCase(wdata(XLEN - 1, 0), Array(
      (size === "b11".U)    ->  wdata(63, 0),
      (size === "b10".U)    ->  wdata(31, 0),
      (size === "b01".U)    ->  wdata(15, 0),
      (size === "b00".U)    ->  wdata(7, 0),
    )).asUInt()
    val shiftwdata = Wire(UInt(XLEN.W))
    shiftwdata := partdata << (addr(2, 0) << 3)
    shiftwdata
  }
  def genWriteBackData(read_words: UInt, mem_op: UInt): UInt = {
    MuxCase(read_words, Array(
      (mem_op === lb)   -> Cat(Fill(XLEN -  8, read_words(7)),  read_words(7, 0)),
      (mem_op === lh)   -> Cat(Fill(XLEN - 16, read_words(15)), read_words(15, 0)),
      (mem_op === lw)   -> Cat(Fill(XLEN - 32, read_words(31)), read_words(31, 0)),
      (mem_op === lbu)  -> Cat(Fill(XLEN -  8, 0.U),            read_words(7, 0)),
      (mem_op === lhu)  -> Cat(Fill(XLEN - 16, 0.U),            read_words(15, 0)),
      (mem_op === lwu)  -> Cat(Fill(XLEN - 32, 0.U),            read_words(31, 0))
    ))
  }
}

class AccessMemBus extends Bundle {
  val r_req   = Decoupled(new SimpleReadAddr)
  val r_resp  = Flipped(DecoupledIO(new SimpleReadData))
  val w_req   = Decoupled(new SimpleWriteAddrData)
}

class AccessMem extends Module {
  val io = IO(new Bundle() {
    val in = Flipped(DecoupledIO(new ExuOutput))
    val out = DecoupledIO(new WriteBackIO)
    val dmem = new AccessMemBus
  })

  val (valid, mem_en, mem_op, exe_result) = (io.in.valid, 
      io.in.bits.mem_en.asBool, 
      io.in.bits.mem_op, 
      io.in.bits.exe_result)
  
  io.in.ready := (!mem_en) || (mem_en && io.dmem.r_resp.valid)

  val req_addr = exe_result

  // load req
  io.dmem.r_req.valid     := valid && mem_en.asBool() && MemOpType.isLoad(mem_op)
  io.dmem.r_req.bits.addr := req_addr

  // store req
  io.dmem.w_req.valid     := valid && mem_en.asBool() && MemOpType.isStore(mem_op)
  io.dmem.w_req.bits.addr := req_addr
  io.dmem.w_req.bits.data := MemOpType.WriteData(addr = req_addr, rs2 = io.in.bits.rs2_data)
  io.dmem.w_req.bits.strb := MemOpType.genMask(addr = req_addr, size = MemOpType.genSize(mem_op))
  io.dmem.w_req.bits.wen  := valid && mem_en.asBool() && MemOpType.isStore(mem_op)


  // load resp
  io.dmem.r_resp.ready := true.B
  val load_data = MemOpType.genWriteBackData(read_words = io.dmem.r_resp.bits.data, mem_op = mem_op)

  val wb_data = Mux(mem_en, load_data, exe_result)

  io.out.valid        := (!mem_en) || (mem_en && MemOpType.isLoad(mem_op) && io.dmem.r_resp.valid) || (mem_en && MemOpType.isStore(mem_op))
  io.out.bits.pc      := io.in.bits.pc
  io.out.bits.instr   := io.in.bits.instr
  io.out.bits.wb_addr := io.in.bits.wb_addr
  io.out.bits.rf_wen  := io.in.bits.rf_wen
  io.out.bits.wb_data := wb_data

}