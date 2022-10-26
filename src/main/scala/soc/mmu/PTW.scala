package soc.mmu

import chisel3._
import chisel3.util._
import bus._
import config._
import soc.cpu.CSRtoMMUBundle

class PTWReq extends Bundle with MMUConst {
  val vpn = UInt(vpnBits.W)
}

class PTWResp extends PTWReq with MMUConst {
  val pte = new PageTableEntry
  val pf = Bool()
  val level = UInt(LevelBits.W)
}

class PTWMasterIO extends Bundle {
  val req = Flipped(DecoupledIO(new PTWReq))
  val resp = DecoupledIO(new PTWResp)
}

class PTW extends Module with MMUConst {
  val numReq = 2
  val io = IO(new Bundle() {
    val tlb = Vec(numReq, new PTWMasterIO)
    val mem = new MasterCpuLinkBus
    val csr = Input(new CSRtoMMUBundle)
  })

  val tlbReqArb     = Module(new Arbiter(new PTWReq, numReq))
  tlbReqArb.io.in(0) <> io.tlb(0).req
  tlbReqArb.io.in(1) <> io.tlb(1).req
  val arbReq = tlbReqArb.io.out


  val req_vpn = RegInit(0.U(vpnBits.W))
  val req_source = RegInit(0.U(1.W))
  val level = RegInit(0.U(LevelBits))
  val readPte = RegInit(0.U.asTypeOf(new PageTableEntry))

  val s_ilde :: s_memReq :: s_memResp :: s_check :: Nil = Enum(4)
  val state = RegInit(s_ilde)

  arbReq.ready := state === s_ilde

  val pte_addr = (((readPte.ppn << vpnSubBits) | req_vpn) << log2Ceil(XLEN / 8))(PAddrBits - 1, 0)

  io.mem.req.valid := state === s_memReq
  io.mem.req.bits.apply(addr = pte_addr, id = 0.U, cmd = CpuLinkCmd.req_read, size = "b11".U)

  val pageFault = readPte.isPageFault(level)
  val max_level = (PageTableLevel-1).U
  val final_pte = (readPte.isLeafPTE() || level === max_level) || pageFault

  for (i <- 0 until numReq) {
    val resp = io.tlb(i).resp

    resp.valid := state === s_check && final_pte && req_source === i.U
    resp.bits.vpn := req_vpn
    resp.bits.pte := readPte
    resp.bits.pf  := pageFault
    resp.bits.level := level
  }

  switch (state) {
    is (s_ilde) {
      when (arbReq.fire) {
        state := s_memReq
        req_vpn := arbReq.bits.vpn
        req_source := tlbReqArb.io.chosen
        readPte.ppn := io.csr.satp.ppn
        level := 0.U
      }
    }
    is (s_memReq) {
      when (io.mem.req.fire) {
        state := s_memResp
      }
    }
    is (s_memResp) {
      when (io.mem.resp.fire) {
        state := s_check
        readPte := io.mem.resp.bits.data.asTypeOf(new PageTableEntry)
      }
    }
    is (s_check) {
      when (final_pte) {
        when (io.tlb(req_source).resp.fire) {
          state := s_ilde
        }
      }.otherwise {
        state := s_memReq
        level := level + 1.U
      }
    }
  }


}
