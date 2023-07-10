package dev.revtools.updater.asm

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.FieldNode

class FieldEntry(cls: ClassEntry, val node: FieldNode) : MemberEntry<FieldEntry>(cls) {

    val id = "${node.name}:${node.desc}"

    val access = node.access
    val name = node.name
    val desc = node.desc
    val value = node.value

    val type: ClassEntry = group.getCreateClassById(desc).also {
        it.fieldTypeRefs.add(this)
    }

    val readRefs = hashSetOf<MethodEntry>()
    val writeRefs = hashSetOf<MethodEntry>()

    fun isPrivate() = (access and Opcodes.ACC_PRIVATE) != 0
    fun isStatic() = (access and Opcodes.ACC_STATIC) != 0

    override fun toString(): String {
        return "${cls.name}.$id"
    }
}