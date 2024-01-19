package simd

import chisel3._
import org.scalatest.flatspec.AnyFlatSpec
import chiseltest._
import scala.math.BigInt
import org.scalatest.matchers.should.Matchers
import org.scalatest.Tag

// import lib for random test data generation
import scala.util.Random
import scala.math.pow

// Post-Processing unit golden model trait
trait HasPostProcessingGoldenModel {
  def postProcessingGoldenModel(
      input: Int,
      inputZp: Byte,
      outputZp: Byte,
      multiplier: Int,
      shift: Byte, // values between 0-63
      maxInt: Byte,
      minInt: Byte,
      doubleRound: Boolean
  ): Byte = {

    // input zero-point adjustment
    var adjustedInput = input - inputZp

    // multiplication
    val var0: Long = adjustedInput.toLong * multiplier.toLong

    // shift & round
    var var1: Int = (var0 >> (shift - 1)).toInt

    if (doubleRound) {
      if (var1 >= 0)
        var1 += 1
      else
        var1 -= 1
    }
    var1 = var1 >> 1

    // output zero-point adjustment
    var1 = var1 + outputZp

    // clamping
    if (var1 > maxInt)
      var1 = maxInt
    if (var1 < minInt)
      var1 = minInt

    val result: Byte = var1.toByte
    result
  }
}

// Random test data generation trait
trait HasRandomTestGen {
  def RandomTestGen(): (Int, Byte, Byte, Int, Byte, Byte, Byte, Boolean) = {
    // Initialize random seed
    val random = new Random(System.currentTimeMillis())

    // Generate random values
    val input: Int = random.nextInt(math.pow(2, 28).toInt)
    val inputZp: Byte = random.nextInt(256).toByte
    val outputZp: Byte = random.nextInt(256).toByte
    var shift: Byte = random.nextInt(38).toByte
    if (shift < 0)
      shift = (-shift).toByte
    val multiplier: Int = (16 * math.pow(2, shift)).toInt
    shift = (shift + 25).toByte

    val maxInt: Byte = 127
    val minInt: Byte = -128
    val doubleRound: Boolean = true

    // Return a tuple of the generated values
    (input, inputZp, outputZp, multiplier, shift, maxInt, minInt, doubleRound)
  }

}

// post-processing PE automated random test data generation, test and result check
class PEManualTest
    extends AnyFlatSpec
    with ChiselScalatestTester
    with Matchers
    with HasPostProcessingGoldenModel
    with HasRandomTestGen {
  "DUT" should "pass" in {
    test(new PE)
      .withAnnotations(
        Seq(WriteVcdAnnotation)
      ) { dut =>
        // function wrapper for sending the configuration and the input data to the PE
        def verify(
            input: Int,
            input_zp: Byte,
            output_zp: Byte,
            multiplier: Int,
            shift: Byte,
            max_int: Byte,
            min_int: Byte,
            double_round: Bool,
            golden_output: Byte
        ) = {
          dut.clock.step()

          // giving input data
          dut.io.input_i.poke(input)
          // giving the configuration
          dut.io.ctrl_i.input_zp_i.poke(input_zp)
          dut.io.ctrl_i.output_zp_i.poke(output_zp)
          dut.io.ctrl_i.multiplier_i.poke(multiplier)
          dut.io.ctrl_i.shift_i.poke(shift)
          dut.io.ctrl_i.max_int_i.poke(max_int)
          dut.io.ctrl_i.min_int_i.poke(min_int)
          dut.io.ctrl_i.double_round_i.poke(double_round)
          dut.clock.step()

          // grab the output data
          val out = dut.io.out_o.peekInt()

          // assert the PE's output data equals to the golden data
          assert(out == golden_output)

          dut.clock.step()
        }

        // manually random data test
        // test rtl with c-spec from
        // https://gist.github.com/jorendumoulin/83352a1e84501ec4a7b3790461fee2bf
        verify(267082502, -59, -118, 8192, 34, 127, -128, true.B, 9)
        verify(71671912, -23, -126, 65536, 37, 127, -128, true.B, -92)
        verify(61791880, -54, 115, 67108864, 47, 127, -128, true.B, 127)
        verify(118289203, 55, 56, 536870912, 50, 127, -128, true.B, 112)
        verify(182938555, -69, -118, 16777216, 45, 127, -128, true.B, -31)
        verify(182938555, -69, -118, 1566, 65, 127, -128, true, -128)

        // test if the golden model matches c spec
        assert(
          9 == postProcessingGoldenModel(
            267082502,
            -59,
            -118,
            8192,
            34,
            127,
            -128,
            doubleRound = true
          ) &&
            -92 == postProcessingGoldenModel(
              71671912,
              -23,
              -126,
              65536,
              37,
              127,
              -128,
              doubleRound = true
            ) &&
            127 == postProcessingGoldenModel(
              61791880,
              -54,
              115,
              67108864,
              47,
              127,
              -128,
              doubleRound = true
            )
            && 112 == postProcessingGoldenModel(
              118289203,
              55,
              56,
              536870912,
              50,
              127,
              -128,
              doubleRound = true
            ) &&
            -31 == postProcessingGoldenModel(
              182938555,
              -69,
              -118,
              16777216,
              45,
              127,
              -128,
              doubleRound = true
            )
            - 128 == postProcessingGoldenModel(
              182938555,
              -69,
              -118,
              1566,
              65,
              127,
              -128,
              doubleRound = true
            )
        )

        // batch test
        val testNum = 100
        for (i <- 0 until testNum) {
          // gen random test data
          val (
            input,
            inputZp,
            outputZp,
            multiplier,
            shift,
            maxInt,
            minInt,
            doubleRound
          ) = RandomTestGen()

          // gen golden value
          val goldenValue = postProcessingGoldenModel(
            input,
            inputZp,
            outputZp,
            multiplier,
            shift,
            maxInt,
            minInt,
            doubleRound
          )

          // verify the dut result with golden value
          verify(
            input,
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
