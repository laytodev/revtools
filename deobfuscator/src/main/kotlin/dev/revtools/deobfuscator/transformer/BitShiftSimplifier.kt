package dev.revtools.deobfuscator.transformer

import dev.revtools.deobfuscator.Transformer
import dev.revtools.deobfuscator.asm.InsnMatcher
import dev.revtools.deobfuscator.asm.tree.ClassGroup
import dev.revtools.deobfuscator.asm.util.intConstant
import dev.revtools.deobfuscator.asm.util.toAbstractInsnNode
import org.objectweb.asm.Opcodes.*
import org.tinylog.kotlin.Logger

class BitShiftSimplifier : Transformer {

    private var count = 0

    override fun run(group: ClassGroup) {
        group.classes.forEach { cls ->
            cls.methods.forEach { method ->
                for(match in CONST_SHIFT_PATTERN.match(method)) {
                    val push = match[0]
                    val bits = push.intConstant!!

                    val opcode = match[1].opcode
                    val mask = if(opcode in LONG_SHIFTS) 63 else 31

                    val simplifiedBits = bits and mask
                    if(simplifiedBits != bits) {
                        method.instructions[push] = simplifiedBits.toAbstractInsnNode()
                        count++
                    }
                }
            }
        }

        Logger.info("Simplified $count bitwise shift operations.")
    }

    companion object {

        private val CONST_SHIFT_PATTERN = InsnMatcher.compile(
            """
                (ICONST|BIPUSH|SIPUSH|LDC)
                (ISHL|ISHR|IUSHR|LSHL|LSHR|LUSHR)
            """.trimIndent()
        )

        private val LONG_SHIFTS = setOf(LSHL, LSHR, LUSHR)
    }
}