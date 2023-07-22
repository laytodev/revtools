package dev.revtools.updater.asm

import dev.revtools.updater.util.identityHashSetOf
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.FieldNode

class FieldInstance(val cls: ClassInstance, val node: FieldNode) : Matchable<FieldInstance>() {

    val group get() = cls.group
    val env get() = cls.env

    val access = node.access
    val name = node.name
    val desc = node.desc
    val value = node.value

    val type get() = Type.getType(desc)
    lateinit var typeClass: ClassInstance internal set

    val parents = identityHashSetOf<FieldInstance>()
    val children = identityHashSetOf<FieldInstance>()

    val readRefs = identityHashSetOf<MethodInstance>()
    val writeRefs = identityHashSetOf<MethodInstance>()

    fun isPrivate() = (access and Opcodes.ACC_PRIVATE) != 0
    fun isStatic() = (access and Opcodes.ACC_STATIC) != 0
    fun isAbstract() = (access and Opcodes.ACC_ABSTRACT) != 0

    override fun toString(): String {
        return "$cls.$name"
    }
}