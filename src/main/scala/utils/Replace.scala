package utils

import chisel3._
import chisel3.util._
import chisel3.util.random.LFSR

abstract class ReplacementPolicy {
  def nBits: Int
  def perSet: Boolean
  def way: UInt
  def miss: Unit
  def hit: Unit
  def access(touch_way: UInt): Unit
  def access(touch_ways: Seq[Valid[UInt]]): Unit
  def state_read: UInt
  def get_next_state(state: UInt, touch_way: UInt): UInt
  def get_next_state(state: UInt, touch_ways: Seq[Valid[UInt]]): UInt = {
    touch_ways.foldLeft(state)((prev, touch_way) => Mux(touch_way.valid, get_next_state(prev, touch_way.bits), prev))
  }
  def get_replace_way(state: UInt): UInt
}

class RandomReplacement(n_ways: Int) extends ReplacementPolicy {
  private val replace = Wire(Bool())
  replace := false.B
  def nBits = 16
  def perSet = false
  private val lfsr = LFSR(n_ways, replace)
  def state_read = WireDefault(lfsr)

  def way = lfsr
  def miss = replace := true.B
  def hit = {}
  def access(touch_way: UInt) = replace := true.B
  def access(touch_ways: Seq[Valid[UInt]]) = replace := true.B
  def get_next_state(state: UInt, touch_way: UInt) = 0.U //DontCare
  def get_replace_way(state: UInt) = way
}

object ReplacementPolicy {
  def fromString(s: String, n_ways: Int, n_sets: Int): SetAssocReplacementPolicy = s.toLowerCase match {
    case "random"    => new SetAssocRandom(n_sets, n_ways)
    case t => throw new IllegalArgumentException(s"unknown Replacement Policy type $t")
  }
}

abstract class SetAssocReplacementPolicy {
  def access(set: UInt, touch_way: UInt): Unit
  def access(sets: Seq[UInt], touch_ways: Seq[Valid[UInt]]): Unit
  def way(set: UInt): UInt
}

class SetAssocRandom(n_sets : Int, n_ways: Int) extends SetAssocReplacementPolicy {
  val random = new RandomReplacement(n_ways)

  def miss(set: UInt) =  random.miss
  def way(set: UInt) = random.way

  def access(set: UInt, touch_way: UInt) = random.access(touch_way)
  def access(sets: Seq[UInt], touch_ways: Seq[Valid[UInt]]) = random.access(touch_ways)

}