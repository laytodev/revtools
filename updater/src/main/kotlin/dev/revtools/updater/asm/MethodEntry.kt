package dev.revtools.updater.asm

import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.MethodNode

class MethodEntry(cls: ClassEntry, val node: MethodNode) : MemberEntry<MethodEntry>(cls) {

    val id = "${node.name}${node.desc}"
    val access = node.access
    val name = node.name
    val desc = node.desc
    val instructions  = node.instructions

    val argTypes = mutableListOf<ClassEntry>()
    val retType = group.getCreateClassById(Type.getReturnType(desc).descriptor)

    val refsIn = hashSetOf<MethodEntry>()
    val refsOut = hashSetOf<MethodEntry>()
    val fieldWriteRefs = hashSetOf<FieldEntry>()
    val fieldReadRefs = hashSetOf<FieldEntry>()
    val classRefs = hashSetOf<ClassEntry>()

    init {
        val args = Type.getArgumentTypes(desc)
        args.forEach { arg ->
            val argCls = group.getCreateClassById(arg.descriptor)
            argTypes.add(argCls)
            classRefs.add(argCls)
            argCls.methodTypeRefs.add(this)
        }

        classRefs.add(retType)
        retType.methodTypeRefs.add(this)
    }

    fun isPrivate() = (access and ACC_PRIVATE) != 0
    fun isStatic() = (access and ACC_STATIC) != 0
    fun isAbstract() = (access and ACC_ABSTRACT) != 0

    override fun toString(): String {
        return "${cls.name}.$id"
    }
}