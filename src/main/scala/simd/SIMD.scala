package simd

import chisel3._
import chisel3.util._
import chisel3.VecInit

// post-processing SIMD data interface
// one big input port, one big output port
class SIMDDataIO extends Bundle {
  // a multi-data input, decoupled interface for handshake
  val input_i =
    Flipped(Decoupled(UInt((SIMDConstant.laneLen * SIMDConstant.inputType).W)))

  // a multi-data output, decoupled interface for handshake
  val out_o = Decoupled(
    UInt((SIMDConstant.laneLen * SIMDConstant.outputType).W)
  )

}

// post-processing SIMD input and output declaration
class SIMDIO extends Bundle {
  // the input data across different PEs shares the same control signal
  val ctrl = Flipped(DecoupledIO(new PECtrl()))
  // decoupled data ports
  val data = new SIMDDataIO()
}

// post-processing SIMD module
// This module implements this spec: specification: https://gist.github.com/jorendumoulin/83352a1e84501ec4a7b3790461fee2bf in parallel
class SIMD(laneLen: Int = SIMDConstant.laneLen)
    extends Module
    with RequireAsyncReset {
  val io = IO(new SIMDIO())

  // generating parallel PEs
  val lane = Seq.fill(laneLen)(Module(new PE()))

  // control csr registers for storing the control data
  val ctrl_csr = Reg(new PECtrl())

  // result from different PEs
  val result = Wire(
    Vec(SIMDConstant.laneLen, SInt(SIMDConstant.outputType.W))
  )
  // storing the result in case needs to output multi-cycles
  val out_reg = RegInit(
    0.U((SIMDConstant.laneLen * SIMDConstant.outputType).W)
  )

  // the receiver isn't ready, needs to send several cycles
  val keep_output = RegInit(0.B)

  // when config valid, store the configuration for later computation
  when(io.ctrl.valid) {
    ctrl_csr := io.ctrl.bits
  }

  // always ready for configuration
  io.ctrl.ready := 1.B

  // give each PE right control signal and data
  // collect the result of each PE
  for (i <- 0 until laneLen) {
    lane(i).io.ctrl_i := ctrl_csr
    lane(i).io.input_i := io.data.input_i
      .bits(
        (i + 1) * SIMDConstant.inputType - 1,
        (i) * SIMDConstant.inputType
      )
      .asSInt
    lane(i).io.valid_i := io.data.input_i.valid
    result(i) := lane(i).io.out_o
  }

  // always valid for new input on less is sending last output
  io.data.input_i.ready := !keep_output

  // if out valid but not ready, keep sneding output valid signal
  keep_output := io.data.out_o.valid & !io.data.out_o.ready

  // if data out is valid from PEs, store the results in case later needs keep sending output data if receiver side is not ready
  when(lane(0).io.valid_o) {
    out_reg := Cat(result)
  }

  // concat every result to a big data bus for output
  // if is keep sending output, send the stored result
  io.data.out_o.bits := Mux(keep_output, out_reg, Cat(result))

  // first valid from PE or keep sending valid if receiver side is not ready
  io.data.out_o.valid := lane(0).io.valid_o || keep_output

}

// Scala main function for generating system verilog file for the post-processing SIMD module
object SIMD extends App {
  emitVerilog(
    new SIMD(SIMDConstant.laneLen),
    Array("--target-dir", "generated/simd")
  )
}
