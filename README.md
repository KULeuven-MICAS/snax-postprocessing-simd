# Post-Processing SIMD Accelerator for SNAX

Post-Processing SIMD Accelerator accelerates the post-processing kernel in TinyML workload. The specification of this kernel is defined [here](https://gist.github.com/jorendumoulin/83352a1e84501ec4a7b3790461fee2bf).Basically, it combines the common element-wise operations, such as scaling, clamping and quantization kernels.

The Post-Processing SIMD Accelerator has a compatible interface with [SNAX core](https://github.com/KULeuven-micas/snitch_cluster) and will be integrated into it. The Post-Processing SIMD Accelerator is written in CHISEL 5.0.0 and is intended to be connected to the SNAX accelerator RISC-V manager core through a SystemVerilog wrapper.

## Microarchitecture
The microarchitecture of the Post-Processing SIMD accelerator is shown below.

<p align="center">
  <img src="./docs/microarch.svg" alt="">
</p>

The accelerator datapath consists of parallel PEs. Each PE implements the post-processing kernel for one input data. With parallel PEs, this accelerator can deal with an input vector and output the results in parallel.

The Post-Processing SIMD accelerator datapath has several CSRs. The control data, such as the input and output zero-point and scaling factor, is written in the CSRs via a CsrManager when all the CSR configurations are valid. When doing post-processing computation, the configuration for the next post-processing operation can already be written into the CsrManager. When the current computation finishes, the SNAX core can send the configuration valid signal then the CSR value in the CsrManager will be loaded in to the Post-Processing SIMD datapath.

## Parameters
The parameter for this Post-Processing SIMD Accelerator is the parallelism factor `laneLen` defined in `Parameter.scala`, indicating how many elements can be processed in one cycle. The default value is 64.

## IO ports
The input and output ports of the Post-Processing SIMD accelerator are shown in the table below.

The Post-Processing SIMD accelerator uses a simplified CSR request/response interface for CSR write/read operation. A more detailed description of the CSR operation interface can be found at [here](https://kuleuven-micas.github.io/snitch_cluster/rm/snax_cluster.html).

The Post-Processing SIMD accelerator uses the Decoupled interface for input and output data. A more detailed description of the Decoupled interface can be found at [here](https://www.chisel-lang.org/docs/explanations/interfaces-and-connections#the-standard-ready-valid-interface-readyvalidio--decoupled).

|Signal bundle| Signals | Signal name in generated SV | Width | Dir | Description |
| - | - | - | - | - | - |
| csr.req | data | io_csr_req_bits_data | 32| In| The write data from CSR request |
|  | addr | io_csr_req_bits_addr | 32| In| The address indicating which CSR to be wrote or read |
|  | write | io_csr_req_bits_write | 1 | In| The signal indicates this request is for CSR write or read |
|  | valid | io_csr_req_valid | 1 | In| The signal indicates if this request is valid |
|  | ready | io_csr_req_ready | 1 | Out| The signal indicates if the accelerator is ready for this CSR operation|
| csr.rsp | data | io_csr_rsp_bits_data | 32| Out| The response data for CSR read operation |
|  | valid | io_csr_rsp_valid | 1 | Out| The signal indicates if this response is valid |
|  | ready | io_csr_rsp_ready | 1 | In| The signal indicates if the SNAX core is ready for this CSR response |
| data.input_i | bits | io_data_input_i_bits | `laneLen * 32`| In| The input data content|
|  | valid | io_data_input_i_valid | 1| In| The signal indicates if this input data is valid |
|  | ready | io_data_input_i_ready | 1| Out| The signal indicates if the accelerator is ready for this input |
| data.out_o | bits | io_data_out_o_bits | `laneLen * 8`| Out| The output data content |
|  | valid | io_data_out_o_valid | 1| Out| The signal indicates if this output data is valid |
|  | ready | io_data_out_o_ready | 1| In| The signal indicates if the SNAX core is ready for this output data|

The  data.input_i.bits are spited into each PE and the results from each PE are gathered to the data.out_o.bits as indicated at the figure below. Each PE share the same control data from CSRs.

<p align="center">
  <img src="./docs/microarch_detail.svg" alt="">
</p>

## Functional description
The Functional description in the mathematical formula of the Post-Processing SIMD Accelerator is defined as below. Vu is the spatial unrolling factor which is indicated by `laneLen`.

```
parfor (si = 0 to Vu -1):
Output[0..Vu-1] = Post-Processing-Fun(Input[0..Vu-1]) // Input and Output both have Vu elements.
```
The Post-Processing-Func above is the post-processing kernel (see below) for TinyML workload. It combines the operation for scaling, clamping and quantization kernels.
```c
int8_t scale_quant_clamp_c_spec(int32_t input, int8_t input_zp, int8_t output_zp,
                         int32_t multiplier,
                         int8_t shift, // values between 0-63
                         int8_t max_int, int8_t min_int, bool double_round) {

  // input zero-point adjustment
  input = input - input_zp;

  // multiplication
  int64_t var0 = (int64_t)input * (int64_t)multiplier;

  // shift & round
  int32_t var1 = var0 >> (shift - 1);

  if (double_round) {
    if (var1 >= 0)
      var1 += 1;
    else
      var1 -= 1;
  }
  var1 = var1 >> 1;

  // output zero-point adjustment
  var1 = var1 + output_zp;

  // clamping
  if (var1 > max_int)
    var1 = max_int;
  if (var1 < min_int)
    var1 = min_int;

  int8_t result = (int8_t)var1;
  return result;
}
```

### CSR definition
The table below lists the CSRs that the Post-Processing SIMD Accelerator uses. offset in the table is defined by the SNAX core. A more detailed explanation of what are these configurations can be found at `PE.scala` and the [post-processing kernel specification](https://gist.github.com/jorendumoulin/83352a1e84501ec4a7b3790461fee2bf).

| Address | CSR name             | Notes                               |
|---------|--------------------------|-------------------------------------|
| offset + 0   | CSR_0        | CSR_0[31:24] = max_int, CSR_0[23:16] = shift, CSR_0[15:8] = output_zp CSR_0[7:0] = input_zp            |
| offset + 1   | CSR_1             | CSR_1[8] = double_round, CSR_1[7:0] = min_int            |
| offset + 2   | CSR_2             | CSR_2 = multiplier          |
| offset + 3   | configValidCSR | any operation (read/write) to this CSR means that the configure is valid           |


## Quick start
### Set up Chisel environment
The instruction for setting up Chisel compilation and simulation environment can be found [here](https://github.com/KULeuven-MICAS/snax-gemm?tab=readme-ov-file#set-up-chisel-environment).

## Run tests
There are three unit tests for single PE: `PEAutoTest`, SIMD datapath: `SIMDAutoTest`, and SIMD top module with a CsrManager: `SIMDTopAutoTest`.

To run all the Post-Processing SIMD accelerator tests, use:
```
sbt test
```
To run a specific test, use:

```
sbt "testOnly simd.${chisel_test_name}"
```
where `chisel_test_name` is the class name of the specific test. For instance, use:
```
sbt "testOnly simd.SIMDTopAutoTest"
```
to run the Post-Processing SIMD accelerator top module test.

## Generate System Verilog file
To generate the corresponding system verilog file for a specific Chisel module, use:
```
sbt "runMain simd.${chisel_module_name}"
```
For instance, to generate the system verilog file for Post-Processing SIMD top module, use:
```
sbt "runMain simd.SIMDTop"
```
