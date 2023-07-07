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

val FieldNode.parents: HashSet<FieldNode> by field { hashSetOf() }
val FieldNode.children: HashSet<FieldNode> by field { hashSetOf() }
val FieldNode.hierarchy get() = hashSetOf<FieldNode>().plus(this).plus(parents).plus(children)

val FieldNode.readRefs: HashSet<MethodNode> by field { hashSetOf() }
val FieldNode.writeRefs: HashSet<MethodNode> by field { hashSetOf() }

val FieldNode.id get() = "${owner.id}.$name"

fun FieldNode.isStatic() = (access and Opcodes.ACC_STATIC) != 0
fun FieldNode.isPrivate() = (access and Opcodes.ACC_PRIVATE) != 0

internal fun FieldNode.reset() {
    parents.clear()
    children.clear()
    readRefs.clear()
    writeRefs.clear()
}