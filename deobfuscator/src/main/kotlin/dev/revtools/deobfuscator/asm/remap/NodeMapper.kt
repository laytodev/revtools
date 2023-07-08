package dev.revtools.deobfuscator.asm.remap

import dev.revtools.deobfuscator.asm.tree.*

class NodeMapper(
    private val group: ClassGroup,
    private val nameMap: NameMap
) : ExtendedRemapper() {

    override fun map(internalName: String): String? {
        return nameMap.get(internalName)
    }

    override fun mapFieldName(owner: String, name: String, descriptor: String): String {
        return map("$owner.$name") ?: name
    }

    override fun mapMethodName(owner: String, name: String, descriptor: String): String {
        return map("$owner.$name$descriptor") ?: name
    }

}