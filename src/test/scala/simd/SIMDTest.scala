package simd

import chisel3._
import org.scalatest.flatspec.AnyFlatSpec
import chiseltest._
import scala.math.BigInt
import org.scalatest.matchers.should.Matchers
import org.scalatest.Tag

// post-processing SIMD manually-generated random data test
// TODO: automated a brunch of random test data (parallel) generation and check
class SIMDManualTest
    extends AnyFlatSpec
    with ChiselScalatestTester
    with Matchers {
  "DUT" should "pass" in {
    test(new SIMD)
      .withAnnotations(
        Seq(WriteVcdAnnotation)
      ) { dut =>
        // function wrapper for sending the configuration and the input data to the SIMD
        def verify(
            input: BigInt,
            input_zp: Byte,
            output_zp: Byte,
            multiplier: Int,
            shift: Byte,
            max_int: Byte,
            min_int: Byte
        ) = {
          dut.clock.step()

          // giving the configuration
          dut.io.ctrl.bits.input_zp_i.poke(input_zp)
          dut.io.ctrl.bits.output_zp_i.poke(output_zp)
          dut.io.ctrl.bits.multiplier_i.poke(multiplier)
          dut.io.ctrl.bits.shift_i.poke(shift)
          dut.io.ctrl.bits.max_int_i.poke(max_int)
          dut.io.ctrl.bits.min_int_i.poke(min_int)
          dut.io.ctrl.bits.double_round_i.poke(1)
          dut.io.ctrl.valid.poke(1.B)
          dut.clock.step()
          dut.io.ctrl.valid.poke(0)

          // giving input data
          dut.clock.step()
          dut.io.data.input_i.bits.poke(input)
          dut.clock.step()

          // manually check SIMD output
          val out = dut.io.data.out_o.bits.peekInt() & ((1 << 8) - 1)
          println(out)
          val out1 = dut.io.data.out_o.bits.peekInt() & (((1 << 8) - 1) << 8)
          println(out1)

          dut.clock.step()
        }

        // function to translate integer to hex for packing two integer into one big BigInt
        def int2hex(width: Int, intValue: Int) = {
          val paddingChar = '0'
          f"$intValue%x".reverse.padTo(width, paddingChar).reverse
        }

        // random data test
        var a = BigInt(int2hex(8, 267082502) + int2hex(8, 267082502), 16)
        verify(a, -59, -118, 8192, 34, 127, -128)

        a = BigInt(int2hex(8, 71671912) + int2hex(8, 71671912), 16)
        verify(a, -23, -126, 65536, 37, 127, -128)

      }
  }
}
