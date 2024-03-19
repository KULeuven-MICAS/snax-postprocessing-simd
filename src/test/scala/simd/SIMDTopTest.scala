package simd

import chisel3._
import org.scalatest.flatspec.AnyFlatSpec
import chiseltest._
import scala.math.BigInt
import org.scalatest.matchers.should.Matchers
import org.scalatest.Tag

import java.lang.Integer.parseInt

trait HasSIMDTopTestUtils extends HasSIMDTestUtils {

  // write csr helper function
  def write_csr[T <: SIMDTop](dut: T, addr: Int, data: String) = {

    // give the data and address to the right ports
    dut.io.csr.req.bits.write.poke(1.B)
    dut.io.csr.req.bits.data.poke(data.U)
    dut.io.csr.req.bits.addr.poke(addr.U)
    dut.io.csr.req.valid.poke(1.B)

    // wait for grant
    while (dut.io.csr.req.ready.peekBoolean() == false) {
      dut.clock.step(1)
    }

    dut.clock.step(1)

    dut.io.csr.req.valid.poke(0.B)

  }

  // read csr helper function
  def read_csr[T <: SIMDTop](dut: T, addr: Int, data: String) = {
    dut.clock.step(1)

    // give the data and address to the right ports
    dut.io.csr.req.bits.write.poke(0.B)
    dut.io.csr.req.bits.data.poke(data.U)
    dut.io.csr.req.bits.addr.poke(addr.U)
    dut.io.csr.req.valid.poke(1.B)

    // wait for grant
    while (dut.io.csr.req.ready.peekBoolean() == false) {
      dut.clock.step(1)
    }

    // wait for valid signal
    while (dut.io.csr.rsp.valid.peekBoolean() == false) {
      dut.clock.step(1)
    }

    // return read csr result
    val result: UInt = dut.io.csr.rsp.bits.data.peek()

    dut.clock.step(1)

    dut.io.csr.req.valid.poke(0.B)

    // give read out ready signal
    dut.io.csr.rsp.ready.poke(1.B)

    result
  }

  // give dut random generated configuration
  def configCsr[T <: SIMDTop](
      dut: T,
      input_zp: Byte,
      output_zp: Byte,
      multiplier: Int,
      shift: Byte,
      max_int: Byte,
      min_int: Byte,
      doubleRound: Boolean
  ) = {

    // csr 0 content according to csr definition
    val csr_0 = "x" + String.format("%02X", max_int) + String.format(
      "%02X",
      shift
    ) + String.format("%02X", output_zp) + String.format("%02X", input_zp)

    // csr 1 content according to csr definition
    var csr_1 = ""
    if (doubleRound) {
      csr_1 = "x" + String.format("%02X", 1) + String.format("%02X", min_int)
    } else {
      csr_1 = "x" + String.format("%02X", min_int)
    }

    // csr 2 content according to csr definition
    var csr_2 = "x" + String.format("%08X", multiplier)

    // for start address
    var csr_3 = "x" + String.format("%02X", 4)
    var csr_4 = "x" + String.format("%02X", 1)

    // set configuration
    write_csr(dut, 0, csr_0)
    write_csr(dut, 1, csr_1)
    write_csr(dut, 2, csr_2)
    write_csr(dut, 3, csr_3)

    // start signal
    write_csr(dut, 4, csr_4)

    // read csr and check
    assert(csr_0.U(32.W).litValue == read_csr(dut, 0, "x00").litValue)
    assert(csr_1.U(32.W).litValue == read_csr(dut, 1, "x00").litValue)
    assert(csr_2.U(32.W).litValue == read_csr(dut, 2, "x00").litValue)

  }

  // give input data big bus to SIMDTop dut
  def giveInputDataSIMDTop[T <: SIMDTop](dut: T, input: BigInt) = {
    // giving input data
    dut.clock.step()
    dut.io.data.input_i.bits.poke(input)
    dut.io.data.input_i.valid.poke(1.B)
    while (dut.io.data.input_i.ready.peekBoolean() == false) {
      dut.clock.step(1)
    }
    dut.clock.step()
    dut.io.data.input_i.valid.poke(0.B)

  }

  // get output form SIMDTop dut and change to array type then verify with golden data
  def checkSIMDOutputSIMDTop[T <: SIMDTop](
      dut: T,
      goldenValue: Array[Byte]
  ) = {

    // manually check SIMD output
    dut.io.data.out_o.ready.poke(1.B)
    while (dut.io.data.out_o.valid.peekBoolean() == false) {
      dut.clock.step(1)
    }
    val out = dut.io.data.out_o.bits.peek()
    // change big bus to array type
    val outSeq = bus2vec(out)

    // output array data verify with golden data
    (outSeq zip goldenValue).foreach { case (out, golden) =>
      assert(out == golden)
    }

  }

  // give input and verify results
  def verifyOutput[T <: SIMDTop](
      dut: T,
      input: BigInt,
      goldenValue: Array[Byte]
  ) = {

    for (i <- 0 until 4) {

      // give input
      giveInputDataSIMDTop(dut, input)

      // get output form dut and change to array type then verify with golden data
      checkSIMDOutputSIMDTop(dut, goldenValue)

    }

  }

}

class SIMDTopAutoTest
    extends AnyFlatSpec
    with ChiselScalatestTester
    with Matchers
    with HasSIMDTopTestUtils {
  "DUT" should "pass" in {
    test(new SIMDTop)
      .withAnnotations(
        Seq(WriteVcdAnnotation)
      ) { dut =>
        // set test number
        val testNum = 100

        // random test data generation
        for (i <- 1 to testNum) {
          val (
            input,
            inputZp,
            outputZp,
            multiplier,
            shift,
            maxInt,
            minInt,
            doubleRound
          ) = TestGen()

          // input data sequence to a big bus
          val inputBus = vec2bus(input)

          // golden value gen
          val goldenValue = goldenGen(
            input,
            inputZp,
            outputZp,
            multiplier,
            shift,
            maxInt,
            minInt,
            doubleRound
          )

          // give dut random generated configuration
          configCsr(
            dut,
            inputZp,
            outputZp,
            multiplier,
            shift,
            maxInt,
            minInt,
            doubleRound
          )

          // give dut input test data and verify result
          verifyOutput(
            dut,
            inputBus,
            goldenValue
          )
        }
      }
  }
}
