package dev.revtools.deobfuscator.transformer

import com.google.common.collect.MultimapBuilder
import dev.revtools.deobfuscator.Deobfuscator.Companion.isDeobfuscatedName
import dev.revtools.deobfuscator.Transformer
import dev.revtools.deobfuscator.asm.tree.*
import dev.revtools.deobfuscator.asm.util.intConstant
import org.objectweb.asm.Type
import org.objectweb.asm.Type.*
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode
import org.tinylog.kotlin.Logger

class UnusedArgumentRemover : Transformer {

    private var count = 0

    override fun run(group: ClassGroup) {
        val classMap = group.classes.associateBy { it.name }
        val rootMethods = hashSetOf<String>()
        val opaqueMethodMap = MultimapBuilder.hashKeys().arrayListValues().build<String, MethodNode>()
        val opaqueMethods = opaqueMethodMap.asMap()

        group.classes.forEach { cls ->
            val supers = group.findSupers(cls)
            cls.methods.forEach { method ->
                if(supers.none { it.getMethod(method.name, method.desc) != null }) {
                    rootMethods.add(method.id)
                }
            }
        }

        group.classes.forEach { cls ->
            for(method in cls.methods) {
                val methodId = group.findOverride(method.owner.name, method.name, method.desc, rootMethods) ?: continue
                opaqueMethodMap.put(methodId, method)
            }
        }

        val itr = opaqueMethods.iterator()
        for((_, method) in itr) {
            if(method.any { !it.hasOpaqueArg() }) itr.remove()
        }

        group.classes.forEach { cls ->
            cls.methods.forEach { method ->
                for(insn in method.instructions) {
                    if(insn !is MethodInsnNode) continue
                    val methodId = group.findOverride(insn.owner, insn.name, insn.desc, opaqueMethods.keys) ?: continue
                    if(insn.previous.intConstant == null) opaqueMethods.remove(methodId)
                }
            }
        }

        opaqueMethodMap.values().forEach { method ->
            val oldDesc = method.desc
            val newDesc = oldDesc.dropLastArg()
            method.desc = newDesc
            count++
        }

        group.classes.flatMap { it.methods }.forEach { method ->
            val insns = method.instructions
            for(insn in insns) {
                if(insn !is MethodInsnNode) continue
                val methodId = group.findOverride(insn.owner, insn.name, insn.desc, opaqueMethods.keys) ?: continue
                insn.desc = insn.desc.dropLastArg()
                val prevInsn = insn.previous
                insns.remove(prevInsn)
            }
        }

        Logger.info("Removed $count unused opaque method arguments.")
    }

    private val MethodNode.lastArgIndex: Int get() {
        val offset = if(isStatic()) 1 else 0
        return (Type.getArgumentsAndReturnSizes(desc) shr 2) - offset - 1
    }

    private fun MethodNode.hasOpaqueArg(): Boolean {
        val argTypes = Type.getArgumentTypes(desc)
        if(argTypes.isEmpty()) return false
        val lastArg = argTypes.last()
        if(lastArg !in arrayOf(BYTE_TYPE, SHORT_TYPE, INT_TYPE)) return false
        if(isAbstract()) return true
        instructions.forEach { insn ->
            if(insn !is VarInsnNode) return@forEach
            if(insn.`var` == lastArgIndex) return false
        }
        return name.isDeobfuscatedName()
    }

    private fun String.dropLastArg(): String {
        val type = Type.getMethodType(this)
        return Type.getMethodDescriptor(type.returnType, *type.argumentTypes.copyOf(type.argumentTypes.size - 1))
    }

    private fun ClassGroup.findSupers(cls: ClassNode): Collection<ClassNode> {
        return cls.interfaces.plus(cls.superName).mapNotNull { this.getClass(it) }.flatMap { this.findSupers(it).plus(it) }
    }

    private fun ClassGroup.findOverride(owner: String, name: String, desc: String, methods: Set<String>): String? {
        val methodId = "$owner.$name$desc"
        if(methodId in methods) return methodId
        if(name.startsWith("<init>")) return null
        val cls = this.getClass(owner) ?: return null
        for(superCls in this.findSupers(cls)) {
            return this.findOverride(superCls.name, name, desc, methods) ?: continue
        }
        return null
    }
}