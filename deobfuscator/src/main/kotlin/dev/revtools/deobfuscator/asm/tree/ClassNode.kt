package dev.revtools.deobfuscator.asm.tree

import dev.revtools.deobfuscator.asm.field
import dev.revtools.deobfuscator.asm.nullField
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.ACC_ABSTRACT
import org.objectweb.asm.Opcodes.ACC_INTERFACE
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import java.util.ArrayDeque

fun ClassNode.init(group: ClassGroup) {
    this.group = group
    methods.forEach { it.init(this) }
    fields.forEach { it.init(this) }
}

var ClassNode.group: ClassGroup by field()

var ClassNode.parent: ClassNode? by nullField()
val ClassNode.children: HashSet<ClassNode> by field { hashSetOf() }
val ClassNode.interfaceClasses: HashSet<ClassNode> by field { hashSetOf() }
val ClassNode.implementers: HashSet<ClassNode> by field { hashSetOf() }

val ClassNode.superClasses get() = hashSetOf<ClassNode>()
    .let { if(parent != null) it.plus(parent!!) else it }
    .plus(interfaceClasses)

val ClassNode.subClasses get() = hashSetOf<ClassNode>()
    .plus(children)
    .plus(implementers)

val ClassNode.id get() = name

fun ClassNode.isInterface() = (access and ACC_INTERFACE) != 0
fun ClassNode.isAbstract() = (access and ACC_ABSTRACT) != 0

fun ClassNode.getMethod(name: String, desc: String) = methods.firstOrNull { it.name == name && it.desc == desc }
fun ClassNode.getField(name: String, desc: String) = fields.firstOrNull { it.name == name && it.desc == desc }

fun ClassNode.findMethod(name: String, desc: String): MethodNode? {
    return getMethod(name, desc) ?: parent?.findMethod(name, desc)
}

fun ClassNode.findField(name: String, desc: String): FieldNode? {
    return getField(name, desc) ?: parent?.findField(name, desc)
}

fun ClassNode.fromBytes(bytes: ByteArray, flags: Int = ClassReader.SKIP_FRAMES): ClassNode {
    val reader = ClassReader(bytes)
    reader.accept(this, flags)
    return this
}

fun ClassNode.toBytes(flags: Int = ClassWriter.COMPUTE_MAXS): ByteArray {
    val writer = ClassWriter(flags)
    this.accept(writer)
    return writer.toByteArray()
}

internal fun ClassNode.reset() {
    parent = null
    children.clear()
    interfaceClasses.clear()
    implementers.clear()
    methods.forEach { it.reset() }
    fields.forEach { it.reset() }
}

internal fun ClassNode.build() {
    if(superName != null) {
        parent = group.getClass(superName)
        parent?.children?.add(this)
    }

    interfaces.mapNotNull { group.getClass(it) }.forEach { itf ->
        interfaceClasses.add(itf)
        itf.implementers.add(this)
    }
}