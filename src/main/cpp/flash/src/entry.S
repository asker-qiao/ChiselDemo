.section entry
.globl _start

#define MEMORY_ADDRESS 0x80000000

#define concat(x, y) x ## y
#define MAP(c, f) c(f)
#define REGS(f) \
      f(1) f( 3) f( 4) f( 5) f( 6) f( 7) f( 8) f( 9) \
f(10) f(11) f(12) f(13) f(14) f(15) f(16) f(17) f(18) f(19) \
f(20) f(21) f(22) f(23) f(24) f(25) f(26) f(27) f(28) f(29) \
f(30) f(31)

#define ClearIntRegfile(n) mv concat(x, n), zero;
#define ClearFpRegfile(n) fmv.d.x concat(f, n), x0;

_start:
  MAP(REGS, ClearIntRegfile)
  
  li t0, 1
  li t1, 0x88
  slli t0, t0, 13
  csrrs x0, misa, t1
  csrrs x0, mstatus, t0

  fmv.d.x f0, zero
  MAP(REGS, ClearFpRegfile)
  
  csrrc x0, mstatus, t0
  csrrc x0, misa, t1
  mv t0, zero
  mv t1, zero
  la sp, boot_stack_top
  call main
  li s0, MEMORY_ADDRESS
  jr s0

  .section .bss.stack
  .globl boot_stack
boot_stack:
  .space 4096 * 16
  .globl boot_stack_top
boot_stack_top:
