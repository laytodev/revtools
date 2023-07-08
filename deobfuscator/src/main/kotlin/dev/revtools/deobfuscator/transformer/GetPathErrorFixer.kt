package dev.revtools.deobfuscator.transformer

import dev.revtools.deobfuscator.Transformer
import dev.revtools.deobfuscator.asm.tree.ClassGroup
import org.objectweb.asm.Opcodes.POP
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.tinylog.kotlin.Logger

class GetPathErrorFixer : Transformer {

    private var count = 0

    override fun run(group: ClassGroup) {
        var visitedCount = 0
        group.classes.forEach { cls ->
            for(method in cls.methods) {
                val insns = method.instructions.toArray()
                for(insn in insns) {
                    if(insn !is MethodInsnNode) continue
                    if(insn.name != "getPath") continue
                    if(++visitedCount == 2) {
                        val pop = InsnNode(POP)
                        val ldc = LdcInsnNode("")
                        method.instructions.insert(insn, pop)
                        method.instructions.insert(pop, ldc)
                        method.instructions.remove(insn)
                        count++
                        break
                    }
                }
            }
        }

        Logger.info("Fixed $count getPath() method call. (This is to fix a bug with FernFlower decompiler of V1_6 bytecode with runtime V1_8.)")
    }
}