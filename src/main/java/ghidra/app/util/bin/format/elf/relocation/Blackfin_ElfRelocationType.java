/* ###
 * Ghidra-Rare additions to ghidra-blackfin.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package ghidra.app.util.bin.format.elf.relocation;

/** Blackfin ELF relocation types (binutils include/elf/bfin.h). */
public enum Blackfin_ElfRelocationType implements ElfRelocationType {

	R_BFIN_UNUSED0(0x00),
	R_BFIN_PCREL5M2(0x01),
	R_BFIN_UNUSED1(0x02),
	R_BFIN_PCREL10(0x03),
	R_BFIN_PCREL12_JUMP(0x04),
	R_BFIN_RIMM16(0x05),
	R_BFIN_LUIMM16(0x06),
	R_BFIN_HUIMM16(0x07),
	R_BFIN_PCREL12_JUMP_S(0x08),
	R_BFIN_PCREL24_JUMP_X(0x09),
	R_BFIN_PCREL24(0x0a),
	R_BFIN_UNUSEDB(0x0b),
	R_BFIN_UNUSEDC(0x0c),
	R_BFIN_PCREL24_JUMP_L(0x0d),
	R_BFIN_PCREL24_CALL_X(0x0e),
	R_BFIN_VAR_EQ_SYMB(0x0f),
	R_BFIN_BYTE_DATA(0x10),
	R_BFIN_BYTE2_DATA(0x11),
	R_BFIN_BYTE4_DATA(0x12),
	R_BFIN_PCREL11(0x13),
	R_BFIN_GOT17M4(0x14),
	R_BFIN_GOTHI(0x15),
	R_BFIN_GOTLO(0x16),
	R_BFIN_FUNCDESC(0x17),
	R_BFIN_FUNCDESC_GOT17M4(0x18),
	R_BFIN_FUNCDESC_GOTHI(0x19),
	R_BFIN_FUNCDESC_GOTLO(0x1a),
	R_BFIN_FUNCDESC_VALUE(0x1b),
	R_BFIN_FUNCDESC_GOTOFF17M4(0x1c),
	R_BFIN_FUNCDESC_GOTOFFHI(0x1d),
	R_BFIN_FUNCDESC_GOTOFFLO(0x1e),
	R_BFIN_GOTOFF17M4(0x1f),
	R_BFIN_GOTOFFHI(0x20),
	R_BFIN_GOTOFFLO(0x21),
	R_BFIN_PLTPC(0x40),
	R_BFIN_GOT(0x41),
	R_BFIN_GNU_VTINHERIT(0x42),
	R_BFIN_GNU_VTENTRY(0x43);

	public final int typeId;

	private Blackfin_ElfRelocationType(int typeId) {
		this.typeId = typeId;
	}

	@Override
	public int typeId() {
		return typeId;
	}
}
