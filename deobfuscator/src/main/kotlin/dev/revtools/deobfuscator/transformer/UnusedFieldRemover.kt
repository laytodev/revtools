package dev.revtools.deobfuscator.transformer

import dev.revtools.deobfuscator.Transformer
import dev.revtools.deobfuscator.asm.tree.ClassGroup
import dev.revtools.deobfuscator.asm.tree.id
import dev.revtools.deobfuscator.asm.tree.isFinal
import org.objectweb.asm.tree.FieldInsnNode
import org.tinylog.kotlin.Logger

class UnusedFieldRemover : Transformer {

    private var count = 0

    override fun run(group: ClassGroup) {
        val usedFields = group.classes.flatMap { it.methods }
            .flatMap { it.instructions.toArray().asIterable() }
            .mapNotNull { it as? FieldInsnNode }
            .map { "${it.owner}.${it.name}" }
            .toSet()

        group.classes.forEach { cls ->
            val fields = cls.fields.iterator()
            while(fields.hasNext()) {
                val field = fields.next()
                if(!usedFields.contains(field.id) && field.isFinal()) {
                    fields.remove()
                    count++
                }
            }
        }

        Logger.info("Removed $count unused fields.")
    }
}