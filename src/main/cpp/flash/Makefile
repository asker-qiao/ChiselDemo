
WORK_DIR  = $(shell pwd)
BUILD_DIR = $(WORK_DIR)/build

# Cross Compilers
CROSS_COMPILE = riscv64-linux-gnu-
AS        = $(CROSS_COMPILE)gcc
CC        = $(CROSS_COMPILE)gcc
CXX       = $(CROSS_COMPILE)g++
LD        = $(CROSS_COMPILE)ld
OBJDUMP   = $(CROSS_COMPILE)objdump
OBJCOPY   = $(CROSS_COMPILE)objcopy

NAME = flash
SRCS = 	./src/main.c \
				./src/entry.S

IMAGE = $(BUILD_DIR)/$(NAME)

OBJs = $(addprefix $(DST_DIR)/, $(addsuffix .o, $(basename $(SRCS))))
DST_DIR  ?= $(WORK_DIR)/build/flash

INC_PATH += $(WORK_DIR)/include
INCFLAGS += $(addprefix -I, $(INC_PATH))

# -ffreestanding 允许重新定义标准库里已经有的函数, 按独立环境编译，该环境可以没有标准库，且对main()函数没有要求。
COMMON_FLAGS = -march=rv64g -mcmodel=medany
CFLAGS += $(COMMON_FLAGS) -ffreestanding -g -Wall
ASFLAGS += $(COMMON_FLAGS) -MMD
LDFLAGS += -T ./linker.ld -melf64lriscv

default: build

$(DST_DIR)/%.o: %.c
	@mkdir -p $(dir $@) 
	@echo + GCC $<
	@echo $(notdir $(OBJs))
	@$(CC) -std=gnu11 $(CFLAGS) -c -o $@ $(realpath $<)

$(DST_DIR)/%.o: %.S
	@mkdir -p $(dir $@) && echo + AS $<
	@echo $(notdir $(OBJs))
	@$(AS) $(ASFLAGS) -c -o $@ $(realpath $<)

$(IMAGE).elf: $(OBJs)
	@echo + LD "->" $(IMAGE_REL).elf
	@$(LD) $(LDFLAGS) -o $(IMAGE).elf --start-group $(OBJs) --end-group

build: $(IMAGE).elf
	@$(OBJDUMP) -d $(IMAGE).elf > $(IMAGE).txt
	@echo + OBJCOPY "->" $(IMAGE_REL).bin
	@$(OBJCOPY) -S --set-section-flags .bss=alloc,contents -O binary $(IMAGE).elf $(IMAGE).bin

clean:
	-rm -rf $(BUILD_DIR)

.PHONY: build clean