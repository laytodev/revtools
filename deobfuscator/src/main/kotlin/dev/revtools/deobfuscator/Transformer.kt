package dev.revtools.deobfuscator

import dev.revtools.deobfuscator.asm.tree.ClassGroup

interface Transformer {

    fun run(group: ClassGroup)

}