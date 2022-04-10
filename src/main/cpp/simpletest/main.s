	.file	"main.c"
	.option pic
	.text
	.align	1
	.globl	main
	.type	main, @function
main:
	addi	sp,sp,-48
	sd	s0,40(sp)
	addi	s0,sp,48
	la	a5,__stack_chk_guard
	ld	a5,0(a5)
	sd	a5,-24(s0)
	sw	zero,-40(s0)
	sw	zero,-36(s0)
	j	.L2
.L3:
	lw	a5,-36(s0)
	slli	a5,a5,2
	addi	a4,s0,-16
	add	a5,a4,a5
	lw	a4,-36(s0)
	sw	a4,-16(a5)
	lw	a5,-36(s0)
	slli	a5,a5,2
	addi	a4,s0,-16
	add	a5,a4,a5
	lw	a5,-16(a5)
	lw	a4,-40(s0)
	addw	a5,a4,a5
	sw	a5,-40(s0)
	lw	a5,-36(s0)
	addiw	a5,a5,1
	sw	a5,-36(s0)
.L2:
	lw	a5,-36(s0)
	sext.w	a4,a5
	li	a5,1
	ble	a4,a5,.L3
.L4:
	j	.L4
	.size	main, .-main
	.ident	"GCC: (Ubuntu 10.3.0-1ubuntu1~20.04) 10.3.0"
	.section	.note.GNU-stack,"",@progbits
