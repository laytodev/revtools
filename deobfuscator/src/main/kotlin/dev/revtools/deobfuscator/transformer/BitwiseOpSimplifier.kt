package dev.revtools.deobfuscator.transformer

import dev.revtools.deobfuscator.Transformer
import dev.revtools.deobfuscator.asm.InsnMatcher
import dev.revtools.deobfuscator.asm.MemberRef
import dev.revtools.deobfuscator.asm.tree.ClassGroup
import dev.revtools.deobfuscator.asm.util.hasCode
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Opcodes.INVOKESTATIC
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.VarInsnNode
import org.tinylog.kotlin.Logger

class BitwiseOpSimplifier : Transformer {

    private var count = 0

    private val methodOps = mutableMapOf<MemberRef, Int>()

    override fun run(group: ClassGroup) {
        group.classes.forEach { cls ->
            for(method in cls.methods) {
                if(!method.hasCode) continue

                if(method.access and ACC_STATIC == 0) continue
                if(method.desc != BITWISE_OP_DESC) continue

                val match = BITWISE_OP_PATTERN.match(method).firstOrNull() ?: continue

                val iload0 = match[0] as VarInsnNode
                val iload1 = match[1] as VarInsnNode
                if(iload0.`var` != 0 || iload1.`var` != 1) continue

                val ref = MemberRef(cls, method)
                methodOps[ref] = match[2].opcode
            }
        }

        group.classes.forEach { cls ->
            val toRemove = cls.methods.filter { methodOps.containsKey(MemberRef(cls, it)) }
            cls.methods.forEach { method ->
                if(method.hasCode) {
                    val insns = method.instructions.iterator()
                    while(insns.hasNext()) {
                        val insn = insns.next()
                        if(insn !is MethodInsnNode || insn.opcode != INVOKESTATIC) continue

                        val opcode = methodOps[MemberRef(insn)]
                        if(opcode != null) {
                            insns.set(InsnNode(opcode))
                            count++
                        }
                    }
                }
            }
        }

        Logger.info("Simplified $count bitwise operations.")
        Logger.info("Removed ${methodOps.size} redundant methods.")
    }

    companion object {

        private val BITWISE_OP_PATTERN = InsnMatcher.compile("^ILOAD ILOAD (IXOR | IAND | IOR) IRETURN$")
        private val BITWISE_OP_DESC = "(II)I"
    }
}