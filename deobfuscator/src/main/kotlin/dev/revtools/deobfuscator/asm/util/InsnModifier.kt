package dev.revtools.deobfuscator.asm.util

import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodNode

class InsnModifier {

    companion object {
        val EMPTY_LIST = InsnList()
    }

    private val replacements = hashMapOf<AbstractInsnNode, InsnList>()
    private val appends = hashMapOf<AbstractInsnNode, InsnList>()
    private val prepends = hashMapOf<AbstractInsnNode, InsnList>()

    fun append(original: AbstractInsnNode, insns: InsnList) {
        appends[original] = insns
    }

    fun prepend(original: AbstractInsnNode, insns: InsnList) {
        prepends[original] = insns
    }

    fun replace(original: AbstractInsnNode, vararg insns: AbstractInsnNode) {
        val lst = InsnList()
        insns.forEach { lst.add(it) }
        replacements[original] = lst
    }

    fun replace(original: AbstractInsnNode, insns: InsnList) {
        replacements[original] = insns
    }

    fun remove(original: AbstractInsnNode) {
        replacements[original] = EMPTY_LIST
    }

    fun removeAll(insns: List<AbstractInsnNode>) {
        insns.forEach { remove(it) }
    }

    fun apply(method: MethodNode) {
        replacements.forEach { (insn, list) ->
            method.instructions.insert(insn, list)
            method.instructions.remove(insn)
        }
        prepends.forEach { (insn, list) ->
            method.instructions.insertBefore(insn, list)
        }
        appends.forEach { (insn, list) ->
            method.instructions.insert(insn, list)
        }
    }
}