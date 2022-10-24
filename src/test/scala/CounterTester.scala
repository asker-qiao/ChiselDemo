package demo

import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import chiseltest.simulator.WriteVcdAnnotation

/**
 * Test the counter by printing out the value at each clock cycle.
 */

class CounterTester extends AnyFlatSpec with ChiselScalatestTester {

  "CounterTester test" should "pass" in {
    test(new Counter(2)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      for (i <- 0 until 5) {
        println(i.toString + ": " + dut.io.out.peek.toString) // dut.io.out.peek.litValue
        dut.clock.step(1)
      }
    }
  }

}

