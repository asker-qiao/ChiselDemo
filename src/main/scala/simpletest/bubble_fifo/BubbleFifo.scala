package demo

import chisel3._
import chisel3.util._

class WriterIO(size: Int) extends Bundle {
  val empty = Output(Bool())
  val write = Input(Bool())
  val din = Input(UInt(size.W)) // data in
}

class ReaderIO(size: Int) extends Bundle {
  val read = Input(Bool())
  val full = Output(Bool())
  val dout = Output(UInt(size.W)) // data out
}


class FifoRegister(size: Int) extends Module {
  val io = IO(new Bundle {
    val enq = new WriterIO(size)
    val deq = new ReaderIO(size)
  })

  val empty :: full :: Nil = Enum(2)
  val stateReg = RegInit(empty)
  val dataReg = RegInit(0.U(size.W))

  when(stateReg === empty) {
    when(io.enq.write) {
      stateReg := full
      dataReg := io.enq.din
    }
  }.elsewhen(stateReg === full) {
    when(io.deq.read) {
      stateReg := empty
      dataReg := 0.U // just to better see empty slots in the waveform
    }
  }.otherwise {
    // There should not be an otherwise state
  }

  io.enq.empty := (stateReg === empty)
  io.deq.full := (stateReg === full)
  io.deq.dout := dataReg
}

/**
 * This is a bubble FIFO.
 */
class BubbleFifo(size: Int, depth: Int) extends Module {
  val io = IO(new Bundle {
    val enq = new WriterIO(size)
    val deq = new ReaderIO(size)
  })

  val buffers = Array.fill(depth) { Module(new FifoRegister(size)) }

  for (i <- 0 until depth - 1) {
    buffers(i + 1).io.enq.din := buffers(i).io.deq.dout
    buffers(i + 1).io.enq.write := buffers(i).io.deq.full
    buffers(i).io.deq.read := buffers(i + 1).io.enq.empty
  }
  
  io.enq <> buffers(0).io.enq
  io.deq <> buffers(depth - 1).io.deq
}