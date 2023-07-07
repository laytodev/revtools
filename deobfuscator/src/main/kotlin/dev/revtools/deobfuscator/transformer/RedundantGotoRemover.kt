package dev.revtools.deobfuscator.transformer

import dev.revtools.deobfuscator.Transformer
import dev.revtools.deobfuscator.asm.tree.ClassGroup
import org.objectweb.asm.Opcodes.GOTO
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.tinylog.kotlin.Logger

class RedundantGotoRemover : Transformer {

    private var count = 0

    override fun run(group: ClassGroup) {
        group.classes.forEach { cls ->
            cls.methods.forEach { method ->
                val insns = method.instructions.iterator()
                while(insns.hasNext()) {
                    val insn = insns.next()
                    if(insn.opcode != GOTO) continue
                    insn as JumpInsnNode
                    val nextInsn = insn.next
                    if(nextInsn == null || nextInsn !is LabelNode) continue
                    if(insn.label == nextInsn) {
                        insns.remove()
                        count++
                    }
                }
            }
        }

        Logger.info("Removed $count redundant goto instructions.")
    }
}