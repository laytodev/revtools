package dev.revtools.deobfuscator.transformer

import dev.revtools.deobfuscator.Transformer
import dev.revtools.deobfuscator.asm.InsnMatcher
import dev.revtools.deobfuscator.asm.tree.ClassGroup
import dev.revtools.deobfuscator.asm.tree.isConstructor
import org.objectweb.asm.Opcodes.RETURN
import org.objectweb.asm.tree.InsnNode
import org.tinylog.kotlin.Logger

class ErrorConstructorRemover : Transformer {

    private var count = 0

    override fun run(group: ClassGroup) {
        group.classes.forEach { cls ->
            for(method in cls.methods) {
                if(!method.isConstructor()) continue
                matchLoop@ for(match in PATTERN.match(method)) {
                    match.forEach { insn ->
                        method.instructions.remove(insn)
                    }
                    method.instructions.add(InsnNode(RETURN))
                    count++
                    continue
                }
            }
        }

        Logger.info("Removed $count Error throwing constructors.")
    }

    companion object {

        private val PATTERN = InsnMatcher.compile(
            """
                (NEW)
                (DUP)
                (INVOKESPECIAL)
                (ATHROW)
            """.trimIndent()
        )
    }
}