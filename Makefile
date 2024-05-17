MK_DIR   := $(dir $(realpath $(lastword $(MAKEFILE_LIST))))

define gen_sv_file
	mkdir -p $(MK_DIR)generated/simd && cd $(MK_DIR) && sbt "runMain simd.$(1)"
endef

CHISEL_GENERATED_DIR = $(MK_DIR)generated

CHISEL_MODULE = SIMD

CHISEL_GENERATED_FILES = $(MK_DIR)generated/simd/$(CHISEL_MODULE).sv

$(CHISEL_GENERATED_FILES):
	$(call gen_sv_file,$(CHISEL_MODULE))

.PHONY: clean-data clean

clean-chisel-generated-sv:
	rm -f -r $(CHISEL_GENERATED_FILES) $(CHISEL_GENERATED_FILES_OLD) $(CHISEL_GENERATED_DIR)

clean: clean-chisel-generated-sv	
