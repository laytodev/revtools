package dev.revtools.deobfuscator.asm.util

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter

fun MethodNode.removeDeadCode(owner: String) {
    var changed: Boolean
    do {
        changed = false

        val analyzer = Analyzer(BasicInterpreter())
        val frames = analyzer.analyze(owner, this)

        val it = instructions.iterator()
        var i = 0
        for (insn in it) {
            if (frames[i++] != null || insn is LabelNode) {
                continue
            }

            it.remove()
            changed = true
        }

        changed = changed or tryCatchBlocks.removeIf { it.isBodyEmpty() }
    } while (changed)
}

val MethodNode.hasCode: Boolean
    get() = access and (Opcodes.ACC_NATIVE or Opcodes.ACC_ABSTRACT) == 0

fun MethodNode.copy(): MethodNode {
    val copy = MethodNode(
        access,
        name,
        desc,
        signature,
        exceptions?.toTypedArray()
    )
    accept(copy)
    return copy
}
