package dev.revtools.deobfuscator.transformer

import dev.revtools.deobfuscator.Transformer
import dev.revtools.deobfuscator.asm.tree.ClassGroup
import org.tinylog.kotlin.Logger

class RuntimeExceptionRemover : Transformer {

    private var count = 0

    override fun run(group: ClassGroup) {
        group.classes.forEach { cls ->
            cls.methods.forEach { method ->
                val tcbs = method.tryCatchBlocks.iterator()
                while(tcbs.hasNext()) {
                    val tcb = tcbs.next()
                    if(tcb.type == "java/lang/RuntimeException") {
                        tcbs.remove()
                        count++
                    }
                }
            }
        }

        Logger.info("Removed $count RuntimeException try-catch blocks.")
    }
}