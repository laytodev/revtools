package dev.revtools.updater.asm

import dev.revtools.updater.util.identityHashSetOf
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.MethodNode

class MethodInstance(val cls: ClassInstance, val node: MethodNode) {

    val group get() = cls.group
    val env get() = cls.env

    val access = node.access
    val name = node.name
    val desc = node.desc
    val instructions = node.instructions
    val tryCatchBlocks = node.tryCatchBlocks

    val type get() = Type.getMethodType(desc)

    lateinit var retType: ClassInstance internal set
    val argTypes = mutableListOf<ClassInstance>()

    val refsIn = identityHashSetOf<MethodInstance>()
    val refsOut = identityHashSetOf<MethodInstance>()
    val fieldReadRefs = identityHashSetOf<FieldInstance>()
    val fieldWriteRefs = identityHashSetOf<FieldInstance>()
    val classRefs = identityHashSetOf<ClassInstance>()

    fun isPrivate() = (access and ACC_PRIVATE) != 0
    fun isStatic() = (access and ACC_STATIC) != 0
    fun isAbstract() = (access and ACC_ABSTRACT) != 0

    fun isInitializer() = name == "<clinit>"
    fun isConstructor() = name == "<init>"

    override fun toString(): String {
        return "$cls.$name$desc"
    }
}