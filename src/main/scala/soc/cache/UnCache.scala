package soc.cache

import chisel3._
import chisel3.util._
import bus._
import config._

class UnCache extends Module with L1CacheConst {
  val io = IO(new Bundle() {
    val cpu = Vec(2, Flipped(new MasterCpuLinkBus))
    val mem = new AXI4
  })

  val arb = Module(new Arbiter(new CpuLinkReq, 2))
  arb.io.in(0) <> io.cpu(0).req
  arb.io.in(1) <> io.cpu(1).req
  val arbReq = arb.io.out

  val reqReg = RegInit(0.U.asTypeOf(chiselTypeOf(io.cpu(0).req.bits)))
  val req_source = RegInit(0.U(1.W))

  val s_idle :: s_memReadReq :: s_memReadResp :: s_memWriteAddrReq :: s_memWriteDataReq :: s_memWriteResp :: Nil = Enum(6)
  val state = RegInit(s_idle)

  io.cpu.map(_.req.ready := state === s_idle)

  io.mem.ar.valid := state === s_memReadReq
  io.mem.ar.bits.addr := reqReg.addr
  io.mem.ar.bits.id := 0.U
  io.mem.ar.bits.len := 0.U
  io.mem.ar.bits.size := reqReg.size
  io.mem.ar.bits.burst := 0.U

  io.mem.aw.valid := state === s_memWriteAddrReq
  io.mem.aw.bits.addr := reqReg.addr
  io.mem.aw.bits.len := 0.U
  io.mem.aw.bits.burst := 0.U

  io.mem.w.valid := state === s_memWriteDataReq
  io.mem.w.bits.data := reqReg.wdata
  io.mem.w.bits.last := true.B
  io.mem.w.bits.strb := reqReg.strb

  for (i <- 0 until 2) {
    io.cpu(i).resp.valid := io.mem.r.valid && req_source === i.U
    io.mem.r.ready := io.cpu(req_source).resp.ready
    io.cpu(i).resp.bits.apply(data = io.mem.r.bits.data, id = io.mem.r.bits.id,
      cmd = Mux(reqReg.cmd === CpuLinkCmd.req_read, CpuLinkCmd.resp_read, CpuLinkCmd.resp_write))
  }

  switch (state) {
    is (s_idle) {
      when (arbReq.fire) {
        req_source := arb.io.chosen
        when (CpuLinkCmd.isWriteReq(arbReq.bits.cmd)) {
          state := s_memWriteAddrReq
        }.otherwise {
          state := s_memReadReq
        }
      }
    }
    is (s_memReadReq) {
      when (io.mem.ar.fire) {
        state := s_memReadResp
      }
    }
    is (s_memReadResp) {
      when (io.mem.r.fire) {
        assert(io.mem.r.bits.last)
        state := s_idle
      }
    }
    is (s_memWriteAddrReq) {
      when (io.mem.aw.fire) {
        state := s_memWriteDataReq
      }
    }
    is (s_memWriteDataReq) {
      when (io.mem.w.fire) {
        assert(io.mem.w.bits.last)
        state := s_memWriteResp
      }
    }
    is (s_memWriteResp) {
      when (io.mem.b.fire) {
        state := s_idle
      }
    }
  }

}
