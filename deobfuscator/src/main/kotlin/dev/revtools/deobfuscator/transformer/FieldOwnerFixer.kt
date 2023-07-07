package dev.revtools.deobfuscator.transformer

import dev.revtools.deobfuscator.Transformer
import dev.revtools.deobfuscator.asm.tree.ClassGroup
import dev.revtools.deobfuscator.asm.tree.isStatic
import org.objectweb.asm.Opcodes.GETSTATIC
import org.objectweb.asm.Opcodes.PUTSTATIC
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.tinylog.kotlin.Logger

class FieldOwnerFixer : Transformer {

    private var count = 0

    override fun run(group: ClassGroup) {
        val resolver = Resolver(group)

        group.classes.forEach { cls ->
            cls.methods.forEach { method ->
                method.instructions.iterator().forEach { insn ->
                    if(insn is FieldInsnNode) {
                        val opcode = insn.opcode
                        val owner = insn.owner
                        val isStatic = opcode == GETSTATIC || opcode == PUTSTATIC
                        insn.owner = resolver.resolve(insn.owner, insn.name, insn.desc, isStatic)
                        if(owner != insn.owner) count++
                    }
                }
            }
        }

        Logger.info("Fixed $count field instruction owners.")
    }

    private class Resolver(private val group: ClassGroup) {

        fun resolve(owner: String, name: String, desc: String, isStatic: Boolean): String {
            var cls = group.getClass(owner) ?: return owner
            while(true) {
                if(cls.containsField(name, desc, isStatic)) {
                    return cls.name
                }
                cls = group.getClass(cls.superName) ?: return cls.superName
            }
        }

        private fun ClassNode.containsField(name: String, desc: String, isStatic: Boolean): Boolean {
            return fields.any { it.name == name && it.desc == desc && it.isStatic() == isStatic }
        }
    }
}