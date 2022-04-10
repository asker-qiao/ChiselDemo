package cpu

import chisel3._
import chisel3.util._

object RV32I {

  // RV32 instructions
  def ADDI    = BitPat("b????????????_?????_000_?????_0010011")
  def SLLI    = if (Config.XLEN == 32) BitPat("b0000000?????_?????_001_?????_0010011") else BitPat("b000000??????_?????_001_?????_0010011")
  def SLTI    = BitPat("b????????????_?????_010_?????_0010011")
  def SLTIU   = BitPat("b????????????_?????_011_?????_0010011")
  def XORI    = BitPat("b????????????_?????_100_?????_0010011")
  def SRLI    = if (Config.XLEN == 32) BitPat("b0000000?????_?????_101_?????_0010011") else BitPat("b000000??????_?????_101_?????_0010011")
  def ORI     = BitPat("b????????????_?????_110_?????_0010011")
  def ANDI    = BitPat("b????????????_?????_111_?????_0010011")
  def SRAI    = if (Config.XLEN == 32) BitPat("b0100000?????_?????_101_?????_0010011") else BitPat("b010000??????_?????_101_?????_0010011")

  def ADD     = BitPat("b0000000_?????_?????_000_?????_0110011")
  def SLL     = BitPat("b0000000_?????_?????_001_?????_0110011")
  def SLT     = BitPat("b0000000_?????_?????_010_?????_0110011")
  def SLTU    = BitPat("b0000000_?????_?????_011_?????_0110011")
  def XOR     = BitPat("b0000000_?????_?????_100_?????_0110011")
  def SRL     = BitPat("b0000000_?????_?????_101_?????_0110011")
  def OR      = BitPat("b0000000_?????_?????_110_?????_0110011")
  def AND     = BitPat("b0000000_?????_?????_111_?????_0110011")
  def SUB     = BitPat("b0100000_?????_?????_000_?????_0110011")
  def SRA     = BitPat("b0100000_?????_?????_101_?????_0110011")

  def AUIPC   = BitPat("b????????????????????_?????_0010111")
  def LUI     = BitPat("b????????????????????_?????_0110111")

  def JAL     = BitPat("b????????????????????_?????_1101111")
  def JALR    = BitPat("b????????????_?????_000_?????_1100111")

  def BNE     = BitPat("b???????_?????_?????_001_?????_1100011")
  def BEQ     = BitPat("b???????_?????_?????_000_?????_1100011")
  def BLT     = BitPat("b???????_?????_?????_100_?????_1100011")
  def BGE     = BitPat("b???????_?????_?????_101_?????_1100011")
  def BLTU    = BitPat("b???????_?????_?????_110_?????_1100011")
  def BGEU    = BitPat("b???????_?????_?????_111_?????_1100011")

  def LB      = BitPat("b????????????_?????_000_?????_0000011")
  def LH      = BitPat("b????????????_?????_001_?????_0000011")
  def LW      = BitPat("b????????????_?????_010_?????_0000011")
  def LBU     = BitPat("b????????????_?????_100_?????_0000011")
  def LHU     = BitPat("b????????????_?????_101_?????_0000011")
  def SB      = BitPat("b???????_?????_?????_000_?????_0100011")
  def SH      = BitPat("b???????_?????_?????_001_?????_0100011")
  def SW      = BitPat("b???????_?????_?????_010_?????_0100011")

  // decode info table
  val table = Array(
    AUIPC -> List(InstrType.u, SrcType.pc,  SrcType.imm, FuType.alu, ALUOpType.ADD,   MemType.N, MemOpType.no, WBType.exe, RfWen.Y),
    LUI   -> List(InstrType.u, SrcType.imm, SrcType.imm, FuType.alu, ALUOpType.COPY1, MemType.N, MemOpType.no, WBType.exe, RfWen.Y),


    LB    -> List(InstrType.i, SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.ADD,  MemType.N, MemOpType.lb,  WBType.exe, RfWen.Y),
    LH    -> List(InstrType.i, SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.ADD,  MemType.N, MemOpType.lh,  WBType.exe, RfWen.Y),
    LW    -> List(InstrType.i, SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.ADD,  MemType.N, MemOpType.lw,  WBType.exe, RfWen.Y),
    LBU   -> List(InstrType.i, SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.ADD,  MemType.N, MemOpType.lbu, WBType.exe, RfWen.Y),
    LHU   -> List(InstrType.i, SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.ADD,  MemType.N, MemOpType.lhu, WBType.exe, RfWen.Y),
    SB    -> List(InstrType.s, SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.ADD,  MemType.N, MemOpType.sb,  WBType.not, RfWen.N),
    SH    -> List(InstrType.s, SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.ADD,  MemType.N, MemOpType.sh,  WBType.not, RfWen.N),
    SW    -> List(InstrType.s, SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.ADD,  MemType.N, MemOpType.sw,  WBType.not, RfWen.N),

    ADDI  -> List(InstrType.i, SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.ADD,  MemType.N, MemOpType.no, WBType.exe, RfWen.Y),
    ANDI  -> List(InstrType.i, SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.AND,  MemType.N, MemOpType.no, WBType.exe, RfWen.Y),
    ORI   -> List(InstrType.i, SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.OR,   MemType.N, MemOpType.no, WBType.exe, RfWen.Y),
    XORI  -> List(InstrType.i, SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.XOR,  MemType.N, MemOpType.no, WBType.exe, RfWen.Y),
    SLTU  -> List(InstrType.i, SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.SLTU, MemType.N, MemOpType.no, WBType.exe, RfWen.Y),
    SLTI  -> List(InstrType.i, SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.SLT,  MemType.N, MemOpType.no, WBType.exe, RfWen.Y),
    SLTIU -> List(InstrType.i, SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.SLTU, MemType.N, MemOpType.no, WBType.exe, RfWen.Y),
    SLLI  -> List(InstrType.i, SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.SLL,  MemType.N, MemOpType.no, WBType.exe, RfWen.Y),
    SRAI  -> List(InstrType.i, SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.SRA,  MemType.N, MemOpType.no, WBType.exe, RfWen.Y),
    SRLI  -> List(InstrType.i, SrcType.reg, SrcType.imm, FuType.alu, ALUOpType.SRL,  MemType.N, MemOpType.no, WBType.exe, RfWen.Y),

    ADD   -> List(InstrType.i, SrcType.reg, SrcType.reg, FuType.alu, ALUOpType.ADD,  MemType.N, MemOpType.no, WBType.exe, RfWen.Y),
    SUB   -> List(InstrType.i, SrcType.reg, SrcType.reg, FuType.alu, ALUOpType.SUB,  MemType.N, MemOpType.no, WBType.exe, RfWen.Y),
 
    // logicImmTyp,
    AND   -> List(InstrType.i, SrcType.reg, SrcType.reg, FuType.alu, ALUOpType.AND,  MemType.N, MemOpType.no, WBType.exe, RfWen.Y),
    OR    -> List(InstrType.i, SrcType.reg, SrcType.reg, FuType.alu, ALUOpType.OR,   MemType.N, MemOpType.no, WBType.exe, RfWen.Y),
    XOR   -> List(InstrType.i, SrcType.reg, SrcType.reg, FuType.alu, ALUOpType.XOR,  MemType.N, MemOpType.no, WBType.exe, RfWen.Y),
    SLT   -> List(InstrType.i, SrcType.reg, SrcType.reg, FuType.alu, ALUOpType.SLT,  MemType.N, MemOpType.no, WBType.exe, RfWen.Y),
    SLL   -> List(InstrType.i, SrcType.reg, SrcType.reg, FuType.alu, ALUOpType.SLL,  MemType.N, MemOpType.no, WBType.exe, RfWen.Y),
    SRA   -> List(InstrType.i, SrcType.reg, SrcType.reg, FuType.alu, ALUOpType.SRA,  MemType.N, MemOpType.no, WBType.exe, RfWen.Y),
    SRL   -> List(InstrType.i, SrcType.reg, SrcType.reg, FuType.alu, ALUOpType.SRL,  MemType.N, MemOpType.no, WBType.exe, RfWen.Y),

    JAL   -> List(InstrType.j, SrcType.pc,  SrcType.imm, FuType.jbu, JBUType.jal,   MemType.N, MemOpType.no, WBType.pc, RfWen.Y),
    JALR  -> List(InstrType.i, SrcType.reg, SrcType.no,  FuType.jbu, JBUType.jalr,  MemType.N, MemOpType.no, WBType.pc, RfWen.Y),

    BEQ   -> List(InstrType.b, SrcType.reg, SrcType.reg, FuType.jbu, JBUType.beq,   MemType.N, MemOpType.no, WBType.not, RfWen.N),
    BNE   -> List(InstrType.b, SrcType.reg, SrcType.reg, FuType.jbu, JBUType.bne,   MemType.N, MemOpType.no, WBType.not, RfWen.N),
    BLT   -> List(InstrType.b, SrcType.reg, SrcType.reg, FuType.jbu, JBUType.blt,   MemType.N, MemOpType.no, WBType.not, RfWen.N),
    BLTU  -> List(InstrType.b, SrcType.reg, SrcType.reg, FuType.jbu, JBUType.bltu,  MemType.N, MemOpType.no, WBType.not, RfWen.N),
    BGE   -> List(InstrType.b, SrcType.reg, SrcType.reg, FuType.jbu, JBUType.bge,   MemType.N, MemOpType.no, WBType.not, RfWen.N),
    BGEU  -> List(InstrType.b, SrcType.reg, SrcType.reg, FuType.jbu, JBUType.bgeu,  MemType.N, MemOpType.no, WBType.not, RfWen.N)
  )
}

object RV64I {
  // RV64 instructions
  def ADDIW   = BitPat("b???????_?????_?????_000_?????_0011011")
  def SLLIW   = BitPat("b0000000_?????_?????_001_?????_0011011")
  def SRLIW   = BitPat("b0000000_?????_?????_101_?????_0011011")
  def SRAIW   = BitPat("b0100000_?????_?????_101_?????_0011011")
  def SLLW    = BitPat("b0000000_?????_?????_001_?????_0111011")
  def SRLW    = BitPat("b0000000_?????_?????_101_?????_0111011")
  def SRAW    = BitPat("b0100000_?????_?????_101_?????_0111011")
  def ADDW    = BitPat("b0000000_?????_?????_000_?????_0111011")
  def SUBW    = BitPat("b0100000_?????_?????_000_?????_0111011")

  def LWU     = BitPat("b???????_?????_?????_110_?????_0000011")
  def LD      = BitPat("b???????_?????_?????_011_?????_0000011")
  def SD      = BitPat("b???????_?????_?????_011_?????_0100011")

  val table = RV32I.table ++ Array(
    LD     -> List(InstrType.i, SrcType.reg, SrcType.no,  FuType.alu, ALUOpType.ADD,   MemType.Y, MemOpType.ld, WBType.exe, RfWen.Y),
    LWU    -> List(InstrType.i, SrcType.reg, SrcType.no,  FuType.alu, ALUOpType.ADD,   MemType.Y, MemOpType.lwu, WBType.exe, RfWen.Y),
    SD     -> List(InstrType.s, SrcType.reg, SrcType.reg, FuType.alu, ALUOpType.ADD,   MemType.Y, MemOpType.sd, WBType.not, RfWen.N),

    ADDW   -> List(InstrType.r, SrcType.reg, SrcType.reg, FuType.alu, ALUOpType.ADDW,  MemType.N, MemOpType.no, WBType.exe, RfWen.Y),
    SUBW   -> List(InstrType.r, SrcType.reg, SrcType.reg, FuType.alu, ALUOpType.SUBW,  MemType.N, MemOpType.no, WBType.exe, RfWen.Y),
    SLLW   -> List(InstrType.r, SrcType.reg, SrcType.reg, FuType.alu, ALUOpType.SLLW,  MemType.N, MemOpType.no, WBType.exe, RfWen.Y),
    SRLW   -> List(InstrType.r, SrcType.reg, SrcType.reg, FuType.alu, ALUOpType.SRLW,  MemType.N, MemOpType.no, WBType.exe, RfWen.Y),
    SRAW   -> List(InstrType.r, SrcType.reg, SrcType.reg, FuType.alu, ALUOpType.SRAW,  MemType.N, MemOpType.no, WBType.exe, RfWen.Y),

    ADDIW  -> List(InstrType.i, SrcType.reg, SrcType.no,  FuType.alu, ALUOpType.ADDW,  MemType.N, MemOpType.no, WBType.exe, RfWen.Y),
    SLLIW  -> List(InstrType.i, SrcType.reg, SrcType.no,  FuType.alu, ALUOpType.SLLW,  MemType.N, MemOpType.no, WBType.exe, RfWen.Y),
    SRLIW  -> List(InstrType.i, SrcType.reg, SrcType.no,  FuType.alu, ALUOpType.SRLW,  MemType.N, MemOpType.no, WBType.exe, RfWen.Y),
    SRAIW  -> List(InstrType.i, SrcType.reg, SrcType.no,  FuType.alu, ALUOpType.SRAW,  MemType.N, MemOpType.no, WBType.exe, RfWen.Y),
  )
}