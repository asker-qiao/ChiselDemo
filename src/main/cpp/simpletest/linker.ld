OUTPUT_ARCH(riscv)
ENTRY(_start)

SECTIONS
{
  . = 0x80000000;
  .text : {
    *(entry)
    *(.text)
  }
  . = ALIGN(4K);
  etext = .;
  srodata = .;
  .rodata : {
    *(.rodata*)
    *(.srodata*)
  }
  . = ALIGN(4K);
  erodata = .;
  sdata = .;
  .data : {
    *(.data*)
    *(.sdata*)
  }
  . = ALIGN(4K);
  edata = .;
  .bss : {
    *(.bss.stack)
    sbss = .;
    *(.bss*)
    *(.sbss .sbss.*)
  }
  . = ALIGN(4K);
  _stack_top = .;
  . = _stack_top + 0x8000;
  _stack_pointer = .;
  . = ALIGN(4K);
  _heap_start = .;
}