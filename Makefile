# sim
BUILD_DIR = ./build
SIM_TOP = SimTop
MillTarget = simulator.GenVerilog

SIM_TOP_V = $(BUILD_DIR)/$(SIM_TOP).v

# scala files
SCALA_SRC_DIR = ./src/main/scala/soc ./src/main/scala/simulator ./src/main/scala/bus ./src/main/scala/utils
SCALA_FILE = $(shell find $(SCALA_SRC_DIR) -name '*.scala')

# outstream
TIMELOG = $(BUILD_DIR)/time.log


$(SIM_TOP_V): $(SCALA_FILE)
	mkdir -p $(@D)
	@echo "\n[mill] Generating Verilog files..." > $(TIMELOG)
	mill ChiselDemo.runMain $(MillTarget) -td $(@D)

sim-verilog: $(SIM_TOP_V)
default: sim-verilog
# verilator simulation
emu: $(SIM_TOP_V)
	$(MAKE) -C ./difftest emu SIM_TOP=$(SIM_TOP) DESIGN_DIR=$(NOOP_HOME) EMU_TRACE=1

emu-run:
	$(MAKE) -C ./difftest emu-run SIM_TOP=$(SIM_TOP) DESIGN_DIR=$(NOOP_HOME) EMU_TRACE=1

emu-clean:
	$(MAKE) -C ./difftest emu-clean SIM_TOP=$(SIM_TOP) DESIGN_DIR=$(NOOP_HOME) 

clean:
	$(MAKE) -C ./difftest clean
	rm -rf ./build

.PHONY: emu emu-run sim-verilog clean emu-clean
