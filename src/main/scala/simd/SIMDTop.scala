package simd

import chisel3._
import chisel3.util._

class SIMDTopIO() extends Bundle {
  val csr = new CsrReqRspIO(SIMDConstant.csrAddrWidth)
  val data = new SIMDDataIO()
}

// post-processing SIMD with uniformed interface: csr (snax side) and data ports (streamer side)
class SIMDTop() extends Module with RequireAsyncReset {

  val io = IO(new SIMDTopIO())

  val csrManager = Module(
    new CsrManager(SIMDConstant.csrNum, SIMDConstant.csrAddrWidth)
  )
  val simd = Module(new SIMD())

  // io.csr and csrManager input connection
  csrManager.io.csr_config_in <> io.csr

  // csrManager output and simd control port connection
  // control signals
  simd.io.ctrl.valid := csrManager.io.csr_config_out.valid
  csrManager.io.csr_config_out.ready := simd.io.ctrl.ready

  // splitting csrManager data ports to the simd configuration ports
  // the meanings of these ports can be found at PE.scala
  // the order is also the same as at PE.scala
  // as each control input port is 8 bits so 4 control input port shares 1 csr
  simd.io.ctrl.bits.input_zp_i := csrManager.io.csr_config_out
    .bits(0)(7, 0)
    .asSInt
  simd.io.ctrl.bits.output_zp_i := csrManager.io.csr_config_out
    .bits(0)(15, 8)
    .asSInt

  // this control input port is 32 bits, so it needs 1 csr
  simd.io.ctrl.bits.multiplier_i := csrManager.io.csr_config_out.bits(2).asSInt

  simd.io.ctrl.bits.shift_i :=
    csrManager.io.csr_config_out.bits(0)(23, 16).asSInt
  simd.io.ctrl.bits.max_int_i :=
    csrManager.io.csr_config_out.bits(0)(31, 24).asSInt

  simd.io.ctrl.bits.min_int_i :=
    csrManager.io.csr_config_out.bits(1)(7, 0).asSInt

  // this control input port is only 1 bit
  simd.io.ctrl.bits.double_round_i :=
    csrManager.io.csr_config_out.bits(1)(8).asBool

  io.data <> simd.io.data

}

object SIMDTop extends App {
  emitVerilog(
    new (SIMDTop),
    Array("--target-dir", "generated/simd")
  )
}
