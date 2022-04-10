package demo

import chisel3._
import chiseltest._
import chiseltest.simulator.WriteVcdAnnotation
import org.scalatest.flatspec.AnyFlatSpec

class FifoTester extends AnyFlatSpec with ChiselScalatestTester {
  "FifoTester test" should "pass" in {
    test(new BubbleFifo(size = 16, depth = 4)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      // Default values for all signals
      dut.io.enq.write.poke(false.B)
      dut.io.enq.din.poke(0.U)
      dut.io.deq.read.poke(false.B)
      dut.clock.step()

      // Write one value and expect it on the deq side
      dut.io.enq.din.poke(0x123.U)
      dut.io.enq.write.poke(true.B)
      dut.clock.step()
      dut.io.enq.din.poke(0xab.U)
      dut.io.enq.write.poke(false.B)
      dut.clock.step(12)
      dut.io.enq.empty.expect(true.B)
      dut.io.deq.full.expect(true.B)
      dut.io.deq.dout.expect(0x123.U)

      // Read it out
      dut.io.deq.read.poke(true.B)
      dut.clock.step()
      dut.io.deq.full.expect(false.B)
      dut.io.deq.read.poke(false.B)
      dut.clock.step()

      // Fill the whole buffer
      // FIFO depth available as dut.depth. Test hard-coded for now.
      var cnt = 1
      dut.io.enq.write.poke(true.B)
      for (_ <- 0 until 12) {
        dut.io.enq.din.poke(cnt.U)
        if (dut.io.enq.empty.peek.litToBoolean)
          cnt += 1
        dut.clock.step()
      }
      println(s"Wrote ${cnt-1} words")
      dut.io.enq.empty.expect(false.B)
      dut.io.deq.full.expect(true.B)
      dut.io.deq.dout.expect(1.U)

      // Now read it back
      var expected = 1
      dut.io.enq.write.poke(false.B)
      dut.io.deq.read.poke(true.B)
      for (_ <- 0 until 12) {
        if (dut.io.deq.full.peek.litToBoolean) {
          dut.io.deq.dout.expect(expected.U)
          expected += 1
        }
        dut.clock.step()
      }

      // Now do a speed test
      dut.io.enq.write.poke(true.B)
      dut.io.deq.read.poke(true.B)
      cnt = 0
      for (i <- 0 until 100) {
        dut.io.enq.din.poke(i.U)
        if (dut.io.enq.empty.peek.litToBoolean)
          cnt += 1
        dut.clock.step()
      }
      val cycles = 100.0 / cnt
      println(s"$cnt words in 100 clock cycles, $cycles clock cycles per word")
      assert(cycles >= 0.99, "Cannot be faster than one clock cycle per word")
    }


  } 
}