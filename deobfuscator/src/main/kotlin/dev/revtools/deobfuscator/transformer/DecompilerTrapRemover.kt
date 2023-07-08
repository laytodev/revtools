package dev.revtools.deobfuscator.transformer

import dev.revtools.deobfuscator.Transformer
import dev.revtools.deobfuscator.asm.tree.ClassGroup
import dev.revtools.deobfuscator.asm.util.nextReal
import org.objectweb.asm.Opcodes.NOP
import org.objectweb.asm.Opcodes.POP
import org.objectweb.asm.tree.InsnNode
import org.tinylog.kotlin.Logger

class DecompilerTrapRemover : Transformer {

    private var count = 0

    override fun run(group: ClassGroup) {
        group.classes.forEach { cls ->
            cls.methods.forEach { method ->
                if(method.tryCatchBlocks.any { it.end.nextReal == null}) {
                    method.instructions.add(InsnNode(NOP))
                    count++
                }
            }
        }

        Logger.info("Fixed $count decompiler trap instructions.")
    }
}