package bus

import chisel3._
import chisel3.util._

class CrossBar1toN[T <: Bundle](io_type: T = new AXI4, addrSpace: List[(Long, Long)]) extends Module with CpuLinkConst {
  val numOut = addrSpace.length
  val io = IO(new Bundle() {
    val in = Flipped(io_type)
    val out = Vec(numOut, io_type)
  })

  def addrMatch(addr: UInt) = {
    val outMatchVec = VecInit(addrSpace.map(
      range => (addr >= range._1.U && addr < (range._1 + range._2).U)))
    VecInit(PriorityEncoderOH(outMatchVec))
  }

  io.in match {
    case in: MasterCpuLinkBus =>
      val req = in.req
      val out = io.out.asTypeOf(Vec(numOut, new MasterCpuLinkBus))

      val addr = req.bits.addr
      val outMatchVec = VecInit(addrSpace.map(
        range => (addr >= range._1.U && addr < (range._1 + range._2).U)))
      val outSelVec = VecInit(PriorityEncoderOH(outMatchVec))

      req.ready := out.zip(outSelVec).map { case (o, m) => o.req.ready && m }.reduce(_ | _)

      for (i <- 0 until numOut) {
        out(i).req.valid := req.valid && outSelVec(i)
        out(i).req.bits.apply(
          addr = req.bits.addr,
          id = req.bits.id,
          cmd = req.bits.cmd,
          size = req.bits.size,
          wdata = req.bits.wdata,
          strb = req.bits.strb)
      }

      // resp
      val respArb = Module(new Arbiter(new CpuLinkResp, numOut))
      respArb.io.in.zip(out).map { case (i, o) => i <> o.resp }
      in.resp <> respArb.io.out

      io.out <> out
    case in: AXI4  =>
      val out = io.out.asTypeOf(Vec(numOut, new AXI4))
      val arMatchVec = addrMatch(in.ar.bits.addr)
      in.ar.ready := out.zip(arMatchVec).map { case (o, m) => o.ar.ready && m }.reduce(_|_)

      for (i <- 0 until numOut) {
        out(i).ar.valid := in.ar.valid && arMatchVec(i)
        out(i).ar.bits := in.ar.bits
      }

      // resp
      val respArb = Module(new Arbiter(new AXI4BundleR, numOut))
      respArb.io.in.zip(out).map{ case (in, o) => in <> o.r}
      in.r <> respArb.io.out

      /**
       * write
       */
      val w_idle :: w_write :: w_resp :: w_error :: Nil = Enum(4)
      val w_state = RegInit(w_idle)
      val awMatchVec = addrMatch(in.aw.bits.addr)
      val w_outIdx = PriorityEncoder(awMatchVec)

      val w_portReg = RegEnable(next = w_outIdx, init = 0.U(log2Up(numOut).W), enable = out(w_outIdx).aw.fire)
      val w_port = Mux(out(w_outIdx).aw.fire, w_outIdx, w_portReg)
      val wreqInvalidAddr = in.aw.valid && !awMatchVec.asUInt.orR

      for( (o, v) <- out.zip(awMatchVec) ) {
        o.aw.bits := in.aw.bits
        o.aw.valid := v && (in.aw.valid && (w_state === w_idle))
        o.b.ready := v
      }

      in.w.ready := out(w_port).w.ready
      out.zipWithIndex.map{ case (o, i) => {
        o.w.valid := in.w.valid && (w_port === i.U)
        o.w.bits := in.w.bits
      }}

      switch (w_state) {
        is (w_idle) {
          when (out(w_port).aw.fire) {
            w_state := w_write
          }
          when (out(w_port).w.fire && out(w_port).w.bits.last) {
            w_state := w_resp
          }
          when (wreqInvalidAddr) {
            w_state := w_error
          }
        }
        is(w_write) {
          when(out(w_port).w.fire && out(w_port).w.bits.last) {
            w_state := w_resp
          }
        }
        is (w_resp) {
          when (out(w_port).b.fire) {
            w_state := w_idle
          }
        }
        is (w_error) {
          when(in.b.fire) {
            w_state := w_idle
          }
        }
      }
      in.b.valid := out(w_port).b.fire() || w_state === w_error
      in.b.bits <> out(w_port).b.bits

      out(w_port).b.ready := in.b.ready
      in.aw.ready := out(w_port).aw.ready || wreqInvalidAddr
      io.out <> out
  }
}

class CrossBarNto1[T <: Data](io_type: T = new AXI4, numIn: Int) extends Module {
  val io = IO(new Bundle() {
    val in = Vec(numIn, Flipped(io_type))
    val out = io_type
  })

  io.out match {
    case out: MasterCpuLinkBus =>
      // req
      val in = io.in.asTypeOf(Vec(numIn, new MasterCpuLinkBus))

      val idReg = RegInit(VecInit(Seq.fill(numIn)(0.U(AXI4Parameters.idBits.W))))
      val v = RegInit(VecInit(Seq.fill(numIn)(false.B)))
      val read_idReg = WireInit(idReg)
      val read_v = WireInit(v)

      val inArb = Module(new Arbiter(new CpuLinkReq, numIn))
      inArb.io.in.zip(in).map{ case (arb_in, i) => arb_in <> i.req  }
      out.req <> inArb.io.out
      for (i <- 0 until numIn) {
        out.req.bits.id := i.U
        when (in(i).req.fire) {
          v(inArb.io.chosen) := true.B
          idReg(inArb.io.chosen) := in(inArb.io.chosen).req.bits.id
          read_v(inArb.io.chosen) := true.B
          read_idReg(inArb.io.chosen) := in(inArb.io.chosen).req.bits.id
        }
      }

      // resp
      for (i <- 0 until numIn) {
        in(i).resp.valid := out.resp.valid && read_v(out.resp.bits.id)
        when (in(i).resp.fire) {
          v(i) := false.B
        }
        in(i).resp.bits <> out.resp.bits
        in(i).resp.bits.id := read_idReg(i)
      }
      io.in <> in

    case out: AXI4  =>
      val in = io.in.asTypeOf(Vec(numIn, new AXI4))
      /**
       * Read Event
       */
      val idReg = RegInit(VecInit(Seq.fill(numIn)(0.U(AXI4Parameters.idBits.W))))
      val v = RegInit(VecInit(Seq.fill(numIn)(false.B)))
      val read_idReg = WireInit(idReg)
      val read_v = WireInit(v)

      val inReadArb = Module(new Arbiter(new AXI4BundleA, numIn))
      inReadArb.io.in.zip(in).map{ case (arb_in, i) => arb_in <> i.ar}
      out.ar <> inReadArb.io.out

      for (i <- 0 until numIn) {
        out.ar.bits.id := i.U
        when (in(i).ar.fire) {
          v(inReadArb.io.chosen) := true.B
          idReg(inReadArb.io.chosen) := in(inReadArb.io.chosen).ar.bits.id
          read_v(inReadArb.io.chosen) := true.B
          read_idReg(inReadArb.io.chosen) := in(inReadArb.io.chosen).ar.bits.id
        }
      }

      for (i <- 0 until numIn) {
        in(i).r.valid := out.r.valid && read_v(out.r.bits.id)
        when (in(i).r.fire) {
          v(i) := false.B
        }
        in(i).r.bits <> out.r.bits
        in(i).r.bits.id := read_idReg(i)
      }

      /**
       * Write Event
       */
      val w_idle :: w_dataReq :: w_resp :: Nil = Enum(3)
      val state = RegInit(w_idle)

      val inWriteArb = Module(new Arbiter(new AXI4BundleA, numIn))
      inWriteArb.io.in.zip(in).map{ case (arb_in, i) => arb_in <> i.aw}
      inWriteArb.io.out.ready := out.aw.ready && state === w_idle

      val w_portReg = RegEnable(next = inWriteArb.io.chosen, init = 0.U(log2Up(numIn).W), enable = inWriteArb.io.out.fire)
      val w_port = Mux(inWriteArb.io.out.fire, inWriteArb.io.chosen, w_portReg)

      out.aw.valid := inWriteArb.io.out.valid && state === w_idle
      out.aw.bits <> inWriteArb.io.out.bits

      // write data
      in.map(_.w.ready := false.B)
      out.w.valid := in(w_port).w.valid
      out.w.bits := in(w_port).w.bits
      in(w_port).w.ready := out.w.ready && ((state === w_dataReq) || (state === w_idle))

      // write response
      in.map(_.b.bits := out.b.bits)
      in.map(_.b.valid := false.B)

      in(w_port).b.valid := out.b.valid && state === w_resp
      out.b.ready := in(w_port).b.ready

      switch (state) {
        is (w_idle) {
          when (out.aw.fire) {
            when (out.w.fire && out.w.bits.last) {
              state := w_resp
            }.otherwise {
              state := w_dataReq
            }
          }
        }
        is (w_dataReq) {
          when (out.w.fire && out.w.bits.last) {
            state := w_resp
          }
        }
        is (w_resp) {
          when (out.b.fire) {
            state := w_idle
          }
        }
      }
      io.in <> in
  }

}