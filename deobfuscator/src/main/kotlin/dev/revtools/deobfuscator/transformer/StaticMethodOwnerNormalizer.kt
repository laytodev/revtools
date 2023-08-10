package dev.revtools.deobfuscator.transformer

import com.google.common.collect.MultimapBuilder
import dev.revtools.deobfuscator.Transformer
import dev.revtools.deobfuscator.asm.tree.ClassGroup
import dev.revtools.deobfuscator.asm.tree.isInitializer
import dev.revtools.deobfuscator.asm.tree.isStatic
import dev.revtools.deobfuscator.asm.tree.owner
import org.objectweb.asm.Type
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

class StaticMethodOwnerNormalizer : Transformer {

    override fun run(group: ClassGroup) {

        val results = MultimapBuilder.hashKeys().arrayListValues().build<String, MemberRef>()

        group.classes.forEach { cls ->
            cls.methods.forEach methodLoop@ { method ->
                if(method.isStatic() && !method.isInitializer()) {
                    results.put(method.id(), MemberRef(method))
                }
            }
        }

        println()
    }

    private fun MethodNode.id(): String {
        return "(${Type.getMethodType(desc).argumentTypes.drop(1).joinToString(",") { it.descriptor }})${Type.getMethodType(desc).returnType.descriptor}"
    }

    private data class MemberDesc(val name: String, val desc: String) {
        constructor(method: MethodNode) : this(method.name, method.desc)
        constructor(methodInsn: MethodInsnNode) : this(methodInsn.name, methodInsn.desc)

        override fun toString(): String {
            return "$name $desc"
        }
    }

    private data class MemberRef(val owner: String, val name: String, val desc: String) : Comparable<MemberRef> {
        constructor(method: MethodNode) : this(method.owner.name, method.name, method.desc)
        constructor(methodInsn: MethodInsnNode) : this(methodInsn.owner, methodInsn.name, methodInsn.desc)
        constructor(owner: String, desc: MemberDesc) : this(owner, desc.name, desc.desc)

        override fun compareTo(other: MemberRef): Int {
            var res = owner.compareTo(other.owner)
            if(res != 0) return res

            res = name.compareTo(other.name)
            if(res != 0) return res

            return desc.compareTo(other.desc)
        }

        override fun toString(): String {
            return "$owner.$name $desc"
        }
    }
}