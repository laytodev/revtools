package dev.revtools.deobfuscator.asm.tree

import dev.revtools.deobfuscator.asm.field
import org.objectweb.asm.Opcodes.ACC_PRIVATE
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

fun MethodNode.init(owner: ClassNode) {
    this.owner = owner
}

var MethodNode.owner: ClassNode by field()
val MethodNode.group get() = owner.group

val MethodNode.parents: HashSet<MethodNode> by field { hashSetOf() }
val MethodNode.children: HashSet<MethodNode> by field { hashSetOf() }
val MethodNode.hierarchy get() = hashSetOf<MethodNode>().plus(this).plus(parents).plus(children)

val MethodNode.refsIn: HashSet<MethodNode> by field { hashSetOf() }
val MethodNode.refsOut: HashSet<MethodNode> by field { hashSetOf() }
val MethodNode.fieldReadRefs: HashSet<FieldNode> by field { hashSetOf() }
val MethodNode.fieldWriteRefs: HashSet<FieldNode> by field { hashSetOf() }
val MethodNode.classRefs: HashSet<ClassNode> by field { hashSetOf() }

val MethodNode.id get() = "${owner.id}.$name$desc"

fun MethodNode.isStatic() = (access and ACC_STATIC) != 0
fun MethodNode.isPrivate() = (access and ACC_PRIVATE) != 0

fun MethodNode.isConstructor() = name == "<init>"
fun MethodNode.isInitializer() = name == "<clinit>"

internal fun MethodNode.reset() {
    parents.clear()
    children.clear()
    refsIn.clear()
    refsOut.clear()
    fieldReadRefs.clear()
    fieldWriteRefs.clear()
    classRefs.clear()
}

