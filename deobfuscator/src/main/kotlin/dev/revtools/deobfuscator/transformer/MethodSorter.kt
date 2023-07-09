package dev.revtools.deobfuscator.transformer

import dev.revtools.deobfuscator.Transformer
import dev.revtools.deobfuscator.asm.tree.ClassGroup
import dev.revtools.deobfuscator.asm.tree.isConstructor
import dev.revtools.deobfuscator.asm.tree.isInitializer
import dev.revtools.deobfuscator.asm.tree.isStatic
import org.objectweb.asm.Type
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.MethodNode
import org.tinylog.kotlin.Logger
import java.lang.reflect.Modifier

class MethodSorter : Transformer {

    private var count = 0

    override fun run(group: ClassGroup) {
        group.classes.forEach { cls ->
            cls.methods = cls.methods.sortedWith(compareBy<MethodNode> { !it.isInitializer() }
                .thenBy { !it.isConstructor() }
                .thenBy { !it.isStatic() }
                .thenBy { it.lineNumber }
                .thenBy { Modifier.toString(it.access and Modifier.methodModifiers()) }
                .thenBy { Type.getMethodType(it.desc).returnType.className }
                .thenBy { it.desc }
                .thenBy { it.name }
            )
            count += cls.methods.size
        }

        Logger.info("Reordered $count methods.")
    }

    private val MethodNode.lineNumber: Int? get() {
        instructions.forEach { insn ->
            if(insn is LineNumberNode) {
                return insn.line
            }
        }
        return null
    }
}