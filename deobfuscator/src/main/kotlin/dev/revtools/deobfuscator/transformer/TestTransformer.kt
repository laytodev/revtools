package dev.revtools.deobfuscator.transformer

import dev.revtools.deobfuscator.Transformer
import dev.revtools.deobfuscator.asm.tree.ClassGroup
import org.tinylog.kotlin.Logger

class TestTransformer : Transformer {

    override fun run(group: ClassGroup) {
        Logger.info("Boom")
    }
}