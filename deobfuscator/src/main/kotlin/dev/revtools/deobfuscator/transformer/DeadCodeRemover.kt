package dev.revtools.deobfuscator.transformer

import dev.revtools.deobfuscator.Transformer
import dev.revtools.deobfuscator.asm.tree.ClassGroup
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import org.tinylog.kotlin.Logger

class DeadCodeRemover : Transformer {

    private var count = 0

    override fun run(group: ClassGroup) {
        group.classes.forEach { cls ->
            cls.methods.forEach { method ->
                val insns = method.instructions.toArray()
                val frames = Analyzer(BasicInterpreter()).analyze(cls.name, method)
                for(i in frames.indices) {
                    if(frames[i] == null) {
                        method.instructions.remove(insns[i])
                        count++
                    }
                }
            }
        }

        Logger.info("Removed $count dead instructions.")
    }
}