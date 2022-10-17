package soc.cpu

import simulator.SimulatorConfig
import soc.cache._
import soc.mmu._

object Config {
  val XLEN: Int = 64
  val AddrBits: Int = 32
  val VAddrBits: Int = 32
  val PAddrBits: Int = 32
  val ResetPC = "h8000_0000"

  val cacheAddrSpace = List(
    (0x0L, 0x80000000L),
    (0x80000000L, 0x80000000L)
  )

  val deviceAddrSpace = List(
    (0x40600000L, 0x10L),
    (0x80000000L, 0x80000000L)
  )

  val L1Config: L1CacheConfig = L1CacheConfig(
    VAddrBits = VAddrBits,
    PAddrBits = PAddrBits
  )

  val mmuConfig: MMUConfig = MMUConfig(
    VAddrBits = VAddrBits,
    PAddrBits = PAddrBits
  )

  val iTlb: L1TLBConfig = L1TLBConfig(
    name = "iTLB",
    entrySize = 8
  )
  val dTlb: L1TLBConfig = L1TLBConfig(
    name = "dTLB",
    entrySize = 8
  )

  val simConfig: SimulatorConfig = SimulatorConfig()
}