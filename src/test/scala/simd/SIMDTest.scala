package simd

import chisel3._
import org.scalatest.flatspec.AnyFlatSpec
import chiseltest._
import scala.math.BigInt
import org.scalatest.matchers.should.Matchers
import org.scalatest.Tag

import scala.util.Random

// a trait including SIMD test util functions
trait HasSIMDTestUtils
    extends HasRandomTestGen
    with HasPostProcessingGoldenModel {

  // generate random input data sequence
  def InputSeqGen(): Seq[Int] = {
    // Initialize random seed
    val random = new Random(System.currentTimeMillis())

    // Generate random values
    val input: Seq[Int] =
      Seq.fill(SIMDConstant.laneLen)(random.nextInt(math.pow(2, 28).toInt))

    input

  }

  // random test data generation
  def TestGen(): (Seq[Int], Byte, Byte, Int, Byte, Byte, Byte, Boolean) = {
    // generate random input data sequence
    val input = InputSeqGen()
    // control data generation, all the input sequence share one set control data
    val (
      _,
      inputZp,
      outputZp,
      multiplier,
      shift,
      maxInt,
      minInt,
      doubleRound
    ) = RandomTestGen()
    // return the generated test data
    (input, inputZp, outputZp, multiplier, shift, maxInt, minInt, doubleRound)

  }

  // input data sequence to a big bus
  def vec2bus(input: Seq[Int]): BigInt = {

    var flattenedUInt = ""

    for (i <- input) {
      flattenedUInt = String.format("%08X", i) + flattenedUInt
    }

    BigInt(flattenedUInt, 16)
  }

  // golden data sequence generation
  def goldenGen(
      input: Seq[Int],
      inputZp: Byte,
      outputZp: Byte,
      multiplier: Int,
      shift: Byte, // values between 0-63
      maxInt: Byte,
      minInt: Byte,
      doubleRound: Boolean
  ) = {

    // map input data sequence to results data sequence
    val result = input.map(i =>
      postProcessingGoldenModel(
        i,
        inputZp,
        outputZp,
        multiplier,
        shift,
        maxInt,
        minInt,
        doubleRound
      )
    )

    // change to array type
    result.toArray
  }

  // split dut output big bus to result array for comparison
  def bus2vec(output: UInt) = {
    var result = Array.ofDim[Byte](SIMDConstant.laneLen)
    // convert corresponding bits to Byte data type
    for (i <- 0 until SIMDConstant.laneLen) {
      result(i) = output(
        (i + 1) * SIMDConstant.outputType,
        i * SIMDConstant.outputType
      ).litValue.toByte
    }
    result
  }

  // give input data big bus to dut
  def giveInputData[T <: SIMD](dut: T, input: BigInt) = {
    // giving input data
    dut.clock.step(1)
    dut.io.data.input_i.bits.poke(input)
    dut.io.data.input_i.valid.poke(1.B)
    while (dut.io.data.input_i.ready.peekBoolean() == false) {
      dut.clock.step(1)
    }
    dut.clock.step(1)

  }

  // get output form dut and change to array type then verify with golden data
  def checkSIMDOutput[T <: SIMD](
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

  // function wrapper for sending the configuration and the input data to the SIMD
  def verify[T <: SIMD](
      dut: T,
      input: BigInt,
      input_zp: Byte,
      output_zp: Byte,
      multiplier: Int,
      shift: Byte,
      max_int: Byte,
      min_int: Byte,
      doubleRound: Bool,
      goldenValue: Array[Byte]
  ) = {
    dut.clock.step(1)

    // giving the configuration
    dut.io.ctrl.bits.input_zp_i.poke(input_zp)
    dut.io.ctrl.bits.output_zp_i.poke(output_zp)
    dut.io.ctrl.bits.multiplier_i.poke(multiplier)
    dut.io.ctrl.bits.shift_i.poke(shift)
    dut.io.ctrl.bits.max_int_i.poke(max_int)
    dut.io.ctrl.bits.min_int_i.poke(min_int)
    dut.io.ctrl.bits.double_round_i.poke(doubleRound)
    dut.io.ctrl.bits.len.poke(1.U)
    dut.io.ctrl.valid.poke(1.B)
    while (dut.io.ctrl.ready.peekBoolean() == false) {
      dut.clock.step(1)
    }
    dut.clock.step(1)

    dut.io.ctrl.valid.poke(0)

    // give input data big bus to dut
    giveInputData(dut, input)

    // get output and check
    checkSIMDOutput(dut, goldenValue)

    dut.clock.step(1)
  }

}

class SIMDAutoTest
    extends AnyFlatSpec
    with ChiselScalatestTester
    with Matchers
    with HasSIMDTestUtils {
  "DUT" should "pass" in {
    test(new SIMD)
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

          // give dut test data and verify result
          verify(
            dut,
            inputBus,
            inputZp,
            outputZp,
            multiplier,
            shift,
            maxInt,
            minInt,
            doubleRound.B,
            goldenValue
          )
        }
      }
  }
}
