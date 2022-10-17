package soc.mmu

import chisel3._
import chisel3.util._
import bus._
import soc.cpu.Config

class PTWReq extends Bundle with MMUConst {
  val vpn = UInt(vpnBits.W)
}

class PTWResp extends Bundle with MMUConst {
  val vpn = UInt(vpnBits.W)
  val pte = new PageTableEntry
  val level = UInt(LevelBits.W)
}

class PTWMasterIO extends Bundle {
  val req = Flipped(DecoupledIO(new PTWReq))
  val resp = DecoupledIO(new PTWResp)
}

class PTW extends Module with MMUConst {
  val io = IO(new Bundle() {
    val tlb = new PTWMasterIO
    val mem = new MasterSimpleBus
  })

  val req_vpn = RegInit(0.U(vpnBits.W))
  val level = RegInit(0.U(LevelBits))
  val readPte = RegInit(0.U.asTypeOf(new PageTableEntry))

  val s_ilde :: s_memReq :: s_memResp :: s_check :: Nil = Enum(4)
  val state = RegInit(s_ilde)

  io.tlb.req.ready := state === s_ilde

  val pte_addr = (((readPte.ppn << vpnSubBits) | req_vpn) << log2Ceil(XLEN / 8))(Config.PAddrBits - 1, 0)

  io.mem.req.valid := state === s_memReq
  io.mem.req.bits.apply(addr = pte_addr, id = 0.U, cmd = SimpleBusCmd.req_read, size = "b11".U)

  val pageFault = readPte.isPageFault(level)
  val max_level = (PageTableLevel-1).U
  val final_pte = (readPte.isLeafPTE() || level === max_level) || pageFault

  io.tlb.resp.valid := state === s_check && final_pte
  io.tlb.resp.bits.vpn := req_vpn
  io.tlb.resp.bits.pte := readPte
  io.tlb.resp.bits.level := level

  switch (state) {
    is (s_ilde) {
      when (io.tlb.req.fire) {
        state := s_memReq
        req_vpn := io.tlb.req.bits.vpn
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
        when (io.tlb.resp.fire) {
          state := s_ilde
        }
      }.otherwise {
        state := s_memReq
        level := level + 1.U
      }
    }
  }


}
