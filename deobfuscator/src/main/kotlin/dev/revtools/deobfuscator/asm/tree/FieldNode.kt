package dev.revtools.deobfuscator.asm.tree

import dev.revtools.deobfuscator.asm.field
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

fun FieldNode.init(owner: ClassNode) {
    this.owner = owner
}

var FieldNode.owner: ClassNode by field()
val FieldNode.group get() = owner.group

val FieldNode.id get() = "${owner.id}.$name"

fun FieldNode.isStatic() = (access and Opcodes.ACC_STATIC) != 0
fun FieldNode.isPrivate() = (access and Opcodes.ACC_PRIVATE) != 0
fun FieldNode.isFinal() = (access and Opcodes.ACC_FINAL) != 0

internal fun FieldNode.reset() {
}