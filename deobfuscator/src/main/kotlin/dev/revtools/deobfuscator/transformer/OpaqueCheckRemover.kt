package dev.revtools.deobfuscator.transformer

import dev.revtools.deobfuscator.Transformer
import dev.revtools.deobfuscator.asm.InsnMatcher
import dev.revtools.deobfuscator.asm.intConstant
import dev.revtools.deobfuscator.asm.tree.ClassGroup
import dev.revtools.deobfuscator.asm.tree.isStatic
import org.objectweb.asm.Opcodes.GOTO
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import org.tinylog.kotlin.Logger

class OpaqueCheckRemover : Transformer {

    private var count = 0

    override fun run(group: ClassGroup) {
        group.classes.forEach { cls ->
            cls.methods.forEach { method ->
                for(match in THROW_PATTERN.match(method).filter { method.checkThrowPattern(it) }) {
                    method.removeCheck(match)
                    continue
                }

                for(match in RETURN_PATTERN.match(method).filter { method.checkReturnPattern(it) }) {
                    method.removeCheck(match)
                    continue
                }
            }
        }

        Logger.info("Removed $count opaque predicate checks.")
    }

    private fun MethodNode.checkThrowPattern(insns: List<AbstractInsnNode>): Boolean {
        val load = insns[0] as VarInsnNode
        val cst = insns[1]
        val new = insns[3] as TypeInsnNode
        if(load.`var` != lastArgIndex) return false
        if(cst.intConstant == null) return false
        return new.desc == "java/lang/IllegalStateException"
    }

    private fun MethodNode.checkReturnPattern(insns: List<AbstractInsnNode>): Boolean {
        val load = insns[0] as VarInsnNode
        val cst = insns[1]
        if(load.`var` != lastArgIndex) return false
        return cst.intConstant != null
    }

    private fun MethodNode.removeCheck(insns: List<AbstractInsnNode>) {
        val jump = insns[2] as JumpInsnNode
        val goto = JumpInsnNode(GOTO, jump.label)
        instructions.insert(insns.last(), goto)
        insns.forEach(instructions::remove)
        count++
    }

    private val MethodNode.lastArgIndex: Int get() {
        val offset = if(isStatic()) 1 else 0
        return (Type.getArgumentsAndReturnSizes(desc) shr 2) - offset - 1
    }

    companion object {

        private val THROW_PATTERN = InsnMatcher.compile(
            """
                (ILOAD)
                ([ICONST_0-LDC])
                ([IF_ICMPEQ-IF_ACMPNE])
                (NEW)
                (DUP)
                (INVOKESPECIAL)
                (ATHROW)
            """.trimIndent()
        )

        private val RETURN_PATTERN = InsnMatcher.compile(
            """
                (ILOAD)
                ([ICONST_0-LDC])
                ([IF_ICMPEQ-IF_ACMPNE])
                ([IRETURN-RETURN])
            """.trimIndent()
        )
    }
}