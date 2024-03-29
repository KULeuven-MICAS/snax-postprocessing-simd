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

  // csrManager parameters, we use 3 CSR for Post-processing SIMD configuration,
  // CSR 4 for the vector length,
  // CSR 5 for performance counter
  // CSR 6 for status CSR (index is 5)
  def csrNum: Int = 6
  def csrAddrWidth: Int = log2Up(csrNum)

}
