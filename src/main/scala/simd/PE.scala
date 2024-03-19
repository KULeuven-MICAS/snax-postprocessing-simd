package simd

import chisel3._
import chisel3.util._

// control signals for the basic processing element, related to the post-processing algorithm
class PECtrl extends Bundle {

  /** @input
    *   input_zp_i, input zero point
    * @input
    *   output_zp_i, output zero point
    * @input
    *   multiplier_i, scaling factor
    * @input
    *   shift_i, shift number
    * @input
    *   max_int_i, maximum number for clamping
    * @input
    *   min_int_i, minimum number for clamping
    * @input
    *   double_round_i, if double round
    */
  val input_zp_i = (SInt(SIMDConstant.constantType.W))
  val output_zp_i = (SInt(SIMDConstant.constantType.W))
  // ! this port has different data width
  val multiplier_i = (SInt(SIMDConstant.constantMulType.W))
  val shift_i = (SInt(SIMDConstant.constantType.W))
  val max_int_i = (SInt(SIMDConstant.constantType.W))
  val min_int_i = (SInt(SIMDConstant.constantType.W))
  val double_round_i = (Bool())

  val len = (UInt(32.W))
}

// processing element input and output declaration
class PEIO extends Bundle {
  val input_i = Input(SInt(SIMDConstant.inputType.W))

  val ctrl_i = Input(new PECtrl())

  val out_o = Output(SInt(SIMDConstant.outputType.W))

  val valid_i = Input(Bool())
  val valid_o = Output(Bool())
}

// processing element module.
// see specification: https://gist.github.com/jorendumoulin/83352a1e84501ec4a7b3790461fee2bf for more details
class PE extends Module with RequireAsyncReset {
  val io = IO(new PEIO())

  val var0 = WireInit(WireInit(0.S((64).W)))
  val var1 = WireInit(WireInit(0.S((32).W)))
  val var2 = WireInit(WireInit(0.S((32).W)))
  val var3 = WireInit(WireInit(0.S((32).W)))

  // for clamp
  val overflow = WireInit(0.B)
  val underflow = WireInit(0.B)

  // post processing operations
  var0 := (io.input_i - io.ctrl_i.input_zp_i) * io.ctrl_i.multiplier_i
  var1 := (var0 >> (io.ctrl_i.shift_i.asUInt - 1.U))(31, 0).asSInt

  when(io.ctrl_i.double_round_i) {
    var2 := (Mux(
      var1 >= 0.S,
      var1 + 1.S,
      var1 - 1.S
    ) >> 1.U) + io.ctrl_i.output_zp_i
  }.otherwise {
    var2 := (var1 >> 1.U) + io.ctrl_i.output_zp_i
  }

  // clamping
  overflow := var2 > io.ctrl_i.max_int_i
  underflow := var2 < io.ctrl_i.min_int_i
  var3 := Mux(
    overflow,
    io.ctrl_i.max_int_i,
    Mux(underflow, io.ctrl_i.min_int_i, var2)
  )

  io.out_o := var3(7, 0).asSInt

  // combination block, output valid when input data is valid
  io.valid_o := io.valid_i
}

// Scala main function for generating system verilog file for the PE module
object PE extends App {
  emitVerilog(
    new (PE),
    Array("--target-dir", "generated/simd")
  )
}
