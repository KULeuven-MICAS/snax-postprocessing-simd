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
  def laneLen = 64

  // csrManager parameters, we use 3 CSR for Post-processing SIMD configuration, + 1 for status CSR
  def csrNum: Int = 3 + 1
  def csrAddrWidth: Int = log2Up(csrNum)

}
