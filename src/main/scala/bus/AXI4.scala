package bus

import chisel3._
import chisel3.util._
import soc.cpu.Config

object AXI4Parameters {
  // These are all fixed by the AXI4 standard:
  val lenBits = 8
  val sizeBits = 3
  val burstBits = 2
  val cacheBits = 4
  val protBits = 3
  val qosBits = 4
  val respBits = 2

  // These are not fixed:
  val idBits = 4
  val addrBits = Config.AddrBits
  val dataBits = Config.XLEN
  val userBits = 1

  def CACHE_RALLOCATE = 8.U(cacheBits.W)
  def CACHE_WALLOCATE = 4.U(cacheBits.W)
  def CACHE_MODIFIABLE = 2.U(cacheBits.W)
  def CACHE_BUFFERABLE = 1.U(cacheBits.W)
  def PROT_PRIVILEDGED = 1.U(protBits.W)
  def PROT_INSECURE = 2.U(protBits.W)
  def PROT_INSTRUCTION = 4.U(protBits.W)

  def BURST_FIXED = 0.U(burstBits.W)
  def BURST_INCR = 1.U(burstBits.W)
  def BURST_WRAP = 2.U(burstBits.W)

  def RESP_OKAY = 0.U(respBits.W)
  def RESP_EXOKAY = 1.U(respBits.W)
  def RESP_SLVERR = 2.U(respBits.W)
  def RESP_DECERR = 3.U(respBits.W)
}

class AXI4LiteBundleA extends Bundle {
  val addr  = Output(UInt(AXI4Parameters.addrBits.W))
  //val prot  = Output(UInt(AXI4Parameters.protBits.W))
}

class AXI4LiteBundleW() extends Bundle {
  val strb = Output(UInt((AXI4Parameters.dataBits/8).W))
  val data  = Output(UInt(AXI4Parameters.dataBits.W))
}

class AXI4LiteBundleB extends Bundle {
  val resp = Output(UInt(AXI4Parameters.respBits.W))
}

class AXI4LiteBundleR() extends AXI4LiteBundleB{
  val data  = Output(UInt(AXI4Parameters.dataBits.W))
}


class AXI4Lite extends Bundle {
  val aw = Decoupled(new AXI4LiteBundleA)
  val w  = Decoupled(new AXI4LiteBundleW)
  val b  = Flipped(Decoupled(new AXI4LiteBundleB))
  val ar = Decoupled(new AXI4LiteBundleA)
  val r  = Flipped(Decoupled(new AXI4LiteBundleR))
}

// AXI4-full
// 突发传输宽度信号 AXSIZE 位宽为 3bit，表示为：传输宽度 = 2 ^ AXSIZE
class AXI4BundleA() extends AXI4LiteBundleA {
  val id = Output(UInt(4.W))
  //val ADDR = Output(UInt(AXI4Parameters.addrBits.W))
  val len = Output(UInt(AXI4Parameters.lenBits.W))  // 猝发长度, 协议中的 AxLen 信号从零开始表示，实际的长度值为 AxLen + 1
  val size = Output(UInt(AXI4Parameters.sizeBits.W))  // 每周期传输数据的字节数量
  val burst = Output(UInt(AXI4Parameters.burstBits.W))
  //val lock = Output(Bool())
  //val cache = Output(UInt(AXI4Parameters.cacheBits.W))
  //val PROT = Output(UInt(AXI4Parameters.qosBits.W))
  //val qos = Output(UInt(4.W)) // 用于每个写交易的地址通道上的4位QoS标识符
  //val region = Output(UInt(4.W))
  //val user = Output(UInt(1.W))
}


class AXI4BundleW() extends AXI4LiteBundleW {
  //val data = Output(UInt(XLEN.W))
  //val strb = Output(UInt((XLEN / 8).W))  // 写选通，对于数据总线的每8位有一个写选通
  val last = Output(Bool()) // 写最后一个
}

class AXI4BundleB() extends AXI4LiteBundleB {
  val id = Output(UInt(4.W))
  //val user = Output(UInt(1.W))
}

class AXI4BundleR extends AXI4LiteBundleR {
  val id = Output(UInt(4.W))
  //val data = Output(UInt(XLEN.W))
  val last = Output(Bool()) // 最后一个
  //val user = Output(UInt(1.W))
}

class AXI4 extends AXI4Lite {
  override val ar = Decoupled(new AXI4BundleA())
  override val r = Flipped(Decoupled(new AXI4BundleR))
  override val aw = Decoupled(new AXI4BundleA())
  override val w = Decoupled(new AXI4BundleW())
  override val b = Flipped(Decoupled(new AXI4BundleB()))
}

class AXI4CrossBar1toN(addrSpace: List[(Long, Long)]) extends Module with SimpleBusConst {
  val numOut = addrSpace.length
  val io = IO(new Bundle() {
    val in = Flipped(new AXI4)
    val out = Vec(numOut, new AXI4)
  })

  def addrMatch(addr: UInt) = {
    val outMatchVec = VecInit(addrSpace.map(
      range => (addr >= range._1.U && addr < (range._1 + range._2).U)))
    VecInit(PriorityEncoderOH(outMatchVec))
  }

  val arMatchVec = addrMatch(io.in.ar.bits.addr)

  io.in.ar.ready := io.out.zip(arMatchVec).map { case (o, m) => o.ar.ready && m }.reduce(_|_)

  for (i <- 0 until numOut) {
    val out = io.out(i)

    out.ar.valid := io.in.ar.valid && arMatchVec(i)
    out.ar.bits := io.in.ar.bits
  }

  // resp
  val respArb = Module(new Arbiter(new AXI4BundleR, numOut))
  respArb.io.in.zip(io.out).map{ case (in, out) => in <> out}
  io.in.r <> respArb.io.out

  /**
   * write
   */
  val w_idle :: w_write :: w_resp :: w_error :: Nil = Enum(4)
  val w_state = RegInit(w_idle)
  val awMatchVec = addrMatch(io.in.aw.bits.addr)
  val w_outIdx = PriorityEncoder(awMatchVec)

  val w_portReg = RegEnable(next = w_outIdx, init = 0.U(log2Up(numOut).W), enable = io.out(w_outIdx).aw.fire)
  val w_port = Mux(io.out(w_outIdx).aw.fire, w_outIdx, w_portReg)
  val wreqInvalidAddr = io.in.aw.valid && !awMatchVec.asUInt.orR

  for( (o, v) <- io.out.zip(awMatchVec) ) {
    o.aw.bits := io.in.aw.bits
    o.aw.valid := v && (io.in.aw.valid && (w_state === w_idle))
    o.b.ready := v
  }

  io.in.w.ready := io.out(w_port).w.ready
  io.out.zipWithIndex.map{ case (o, i) => {
    o.w.valid := io.in.w.valid && (w_port === i.U)
    o.w.bits := io.in.w.bits
  }}

  switch (w_state) {
    is (w_idle) {
      when (io.out(w_port).aw.fire) {
        w_state := w_write
      }
      when (io.out(w_port).w.fire && io.out(w_port).w.bits.last) {
        w_state := w_resp
      }
      when (wreqInvalidAddr) {
        w_state := w_error
      }
    }
    is(w_write) {
      when(io.out(w_port).w.fire && io.out(w_port).w.bits.last) {
        w_state := w_resp
      }
    }
    is (w_resp) {
      when (io.out(w_port).b.fire) {
        w_state := w_idle
      }
    }
    is (w_error) {
      when(io.in.b.fire) {
        w_state := w_idle
      }
    }
  }
  io.in.b.valid := io.out(w_port).b.fire() || w_state === w_error
  io.in.b.bits <> io.out(w_port).b.bits

  io.out(w_port).b.ready := io.in.b.ready
  io.in.aw.ready := io.out(w_port).aw.ready || wreqInvalidAddr
}

class AXI4CrossBarNto1(numIn: Int) extends Module {
  val io = IO(new Bundle() {
    val in = Vec(numIn, Flipped(new AXI4))
    val out = new AXI4
  })

  /**
   * Read Event
   */
  val idReg = RegInit(VecInit(Seq.fill(numIn)(0.U(AXI4Parameters.idBits.W))))
  val v = RegInit(VecInit(Seq.fill(numIn)(false.B)))
  val read_idReg = WireInit(idReg)
  val read_v = WireInit(v)
  val inReadArb = Module(new Arbiter(new AXI4BundleA, numIn))
  inReadArb.io.in.zip(io.in).map{ case (arb_in, in) => arb_in <> in.ar}
  io.out.ar <> inReadArb.io.out
  for (i <- 0 until numIn) {
    inReadArb.io.in(i) <> io.in(i).ar
    io.out.ar <> inReadArb.io.out
    io.out.ar.bits.id := i.U
    when (io.in(i).ar.fire) {
      v(inReadArb.io.chosen) := true.B
      idReg(inReadArb.io.chosen) := io.in(inReadArb.io.chosen).ar.bits.id
      read_v(inReadArb.io.chosen) := true.B
      read_idReg(inReadArb.io.chosen) := io.in(inReadArb.io.chosen).ar.bits.id
    }
  }

  for (i <- 0 until numIn) {
    io.in(i).r.valid := io.out.r.valid && read_v(io.out.r.bits.id)
    when (io.in(i).r.fire) {
      v(i) := false.B
    }
    io.in(i).r.bits <> io.out.r.bits
    io.in(i).r.bits.id := read_idReg(i)
  }

  /**
   * Write Event
   */
  val w_idle :: w_dataReq :: w_resp :: Nil = Enum(3)
  val state = RegInit(w_idle)

  val inWriteArb = Module(new Arbiter(new AXI4BundleA, numIn))
  inWriteArb.io.in.zip(io.in).map{ case (arb_in, in) => arb_in <> in.aw}
  inWriteArb.io.out.ready := io.out.aw.ready && state === w_idle

  val w_portReg = RegEnable(next = inWriteArb.io.chosen, init = 0.U(log2Up(numIn).W), enable = inWriteArb.io.out.fire)
  val w_port = Mux(inWriteArb.io.out.fire, inWriteArb.io.chosen, w_portReg)

  io.out.aw.valid := inWriteArb.io.out.valid && state === w_idle
  io.out.aw.bits <> inWriteArb.io.out.bits

  // write data
  io.in.map(_.w.ready := false.B)
  io.out.w.valid := io.in(w_port).w.valid
  io.out.w.bits := io.in(w_port).w.bits
  io.in(w_port).w.ready := io.out.w.ready && ((state === w_dataReq) || (state === w_idle))

  // write response
  io.in.map(_.b.bits := io.out.b.bits)
  io.in.map(_.b.valid := false.B)


  io.in(w_port).b.valid := io.out.b.valid && state === w_resp
  io.out.b.ready := io.in(w_port).b.ready

  switch (state) {
    is (w_idle) {
      when (io.out.aw.fire) {
        when (io.out.w.fire && io.out.w.bits.last) {
          state := w_resp
        }.otherwise {
          state := w_dataReq
        }
      }
    }
    is (w_dataReq) {
      when (io.out.w.fire && io.out.w.bits.last) {
        state := w_resp
      }
    }
    is (w_resp) {
      when (io.out.b.fire) {
        state := w_idle
      }
    }
  }

}