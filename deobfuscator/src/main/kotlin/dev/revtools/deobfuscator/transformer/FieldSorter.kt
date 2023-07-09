package dev.revtools.deobfuscator.transformer

import dev.revtools.deobfuscator.Transformer
import dev.revtools.deobfuscator.asm.MemberDesc
import dev.revtools.deobfuscator.asm.tree.ClassGroup
import dev.revtools.deobfuscator.asm.tree.isStatic
import org.objectweb.asm.Opcodes.PUTFIELD
import org.objectweb.asm.Opcodes.PUTSTATIC
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.FieldNode
import org.tinylog.kotlin.Logger

class FieldSorter : Transformer {

    private var count = 0

    override fun run(group: ClassGroup) {
        group.classes.forEach { cls ->
            sortFields(cls, "<clinit>", PUTSTATIC)
            sortFields(cls, "<init>", PUTFIELD)
        }

        Logger.info("Reordered $count fields.")
    }

    private fun sortFields(cls: ClassNode, ctorName: String, opcode: Int) {
        val ctor = cls.methods.find { it.name == ctorName } ?: return

        val fields = mutableMapOf<MemberDesc, Int>()
        var index = 0
        for(insn in ctor.instructions) {
            if(insn.opcode != opcode) continue

            val putfield = insn as FieldInsnNode
            if(putfield.owner != cls.name) continue

            val desc = MemberDesc(putfield)
            if(!fields.containsKey(desc)) {
                fields[desc] = index++
            }
        }

        cls.fields.sortedWith(STATIC_COMPARATOR.thenBy { fields.getOrDefault(MemberDesc(it), -1) })
        count += cls.fields.size
    }

    companion object {

        private val STATIC_COMPARATOR = Comparator<FieldNode> { a, b ->
            val aStatic = a.isStatic()
            val bStatic = b.isStatic()
            when {
                aStatic && !bStatic -> -1
                !aStatic && bStatic -> 1
                else -> 0
            }
        }
    }
}