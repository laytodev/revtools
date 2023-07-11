package dev.revtools.deobfuscator.transformer

import dev.revtools.deobfuscator.Transformer
import dev.revtools.deobfuscator.asm.MemberDesc
import dev.revtools.deobfuscator.asm.tree.ClassGroup
import dev.revtools.deobfuscator.asm.tree.isStatic
import org.objectweb.asm.Opcodes.PUTFIELD
import org.objectweb.asm.Opcodes.PUTSTATIC
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.FieldNode
import org.tinylog.kotlin.Logger
import java.lang.reflect.Modifier

class FieldSorter : Transformer {

    private var count = 0

    override fun run(group: ClassGroup) {
        group.classes.forEach { cls ->
            count += cls.fields.size
            cls.fields = cls.fields.sortedWith(compareBy<FieldNode> { !it.isStatic() }
                .thenBy { Modifier.toString(it.access and Modifier.fieldModifiers()) }
                .thenBy { Type.getType(it.desc).className }
                .thenBy { it.name }
            )
        }

        Logger.info("Reordered $count fields.")
    }
}