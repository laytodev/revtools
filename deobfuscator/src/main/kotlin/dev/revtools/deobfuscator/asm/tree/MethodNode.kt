package dev.revtools.deobfuscator.asm.tree

import dev.revtools.deobfuscator.asm.field
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

fun MethodNode.init(owner: ClassNode) {
    this.owner = owner
}

var MethodNode.owner: ClassNode by field()
val MethodNode.group get() = owner.group

val MethodNode.id get() = "${owner.id}.$name$desc"

fun MethodNode.isStatic() = (access and ACC_STATIC) != 0
fun MethodNode.isPrivate() = (access and ACC_PRIVATE) != 0
fun MethodNode.isAbstract() = (access and ACC_ABSTRACT) != 0

fun MethodNode.isConstructor() = name == "<init>"
fun MethodNode.isInitializer() = name == "<clinit>"

val MethodNode.virtualMethods: List<MethodNode> get() {
    val ret = mutableListOf<MethodNode>()
    if(isStatic()) {
        ret.add(this)
        return ret
    }
    getSuperMethods(mutableListOf(), owner, name, desc).forEach { method ->
        addChildMethods(ret, hashSetOf(), method.owner, method.name, method.desc)
    }
    return ret
}

private fun getSuperMethods(methods: MutableList<MethodNode>, cls: ClassNode?, name: String, desc: String): MutableList<MethodNode> {
    if(cls == null) return methods

    val method = cls.getMethod(name, desc)
    if(method != null && !method.isStatic()) {
        methods.add(method)
    }

    val superMethods = getSuperMethods(mutableListOf(), cls.parent, name, desc)
    cls.interfaceClasses.forEach { superMethods.addAll(getSuperMethods(mutableListOf(), it, name, desc)) }

    return if(superMethods.isEmpty()) methods else superMethods
}

private fun addChildMethods(methods: MutableList<MethodNode>, visited: HashSet<ClassNode>, cls: ClassNode?, name: String, desc: String) {
    if(cls == null || visited.contains(cls)) return
    visited.add(cls)

    val method = cls.getMethod(name, desc)
    if(method != null && !method.isStatic()) {
        methods.add(method)
    }

    cls.subClasses.forEach { childCls ->
        addChildMethods(methods, visited, childCls, name, desc)
    }
}

internal fun MethodNode.reset() {
}

