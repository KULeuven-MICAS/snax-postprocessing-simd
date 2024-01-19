package simd

import chisel3._
import chisel3.util._

// parameters for post-processing SIMD accelerator
object SIMDConstant {

  // data width
  def inputType = 32
  def outputType = 8
  def constantType = 8
  def constantMulType = 32

  // SIMD parallelism
  def laneLen = 8 * 8
}
