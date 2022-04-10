.section entry
.globl _start

_start:
  mv s0, zero
  la sp, _stack_pointer
  jal main