package dev.revtools.deobfuscator.transformer

import dev.revtools.deobfuscator.Transformer
import dev.revtools.deobfuscator.asm.analysis.DataFlowAnalyzer
import dev.revtools.deobfuscator.asm.tree.ClassGroup
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.IincInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode
import org.tinylog.kotlin.Logger

class CopyPropagationFixer : Transformer {

    private var count = 0

    override fun run(group: ClassGroup) {
        group.classes.forEach { cls ->
            cls.methods.forEach { method ->
                val analyzer = CopyPropagationAnalyzer(method)
                analyzer.analyze()

                for(insn in method.instructions) {
                    if(insn !is VarInsnNode || insn.opcode !in CopyPropagationAnalyzer.LOAD_OPCODES) {
                        continue
                    }

                    val set = analyzer.getInSet(insn) ?: continue
                    val assignment = set.singleOrNull { it.dest == insn.`var` } ?: continue

                    insn.`var` = assignment.src
                    count++
                }
            }
        }

        Logger.info("Propagated $count method variable copies.")
    }

    private data class CopyAssignment(val dest: Int, val src: Int)

    private class CopyPropagationAnalyzer(method: MethodNode) : DataFlowAnalyzer<Set<CopyAssignment>>(method) {

        private val assignments = mutableSetOf<CopyAssignment>()

        init {
            for(insn in method.instructions) {
                if(insn !is VarInsnNode || insn.opcode !in STORE_OPCODES) {
                    continue
                }

                val prevInsn = insn.previous
                if(prevInsn !is VarInsnNode || prevInsn.opcode !in LOAD_OPCODES) {
                    continue
                }

                assignments += CopyAssignment(insn.`var`, prevInsn.`var`)
            }
        }

        override fun createEntrySet(): Set<CopyAssignment> = emptySet()
        override fun createInitialSet(): Set<CopyAssignment> = assignments

        override fun join(set1: Set<CopyAssignment>, set2: Set<CopyAssignment>): Set<CopyAssignment> {
            return set1 intersect set2
        }

        override fun transfer(set: Set<CopyAssignment>, insn: AbstractInsnNode): Set<CopyAssignment> {
            return when {
                insn is VarInsnNode && insn.opcode in STORE_OPCODES -> {
                    val newSet = set.minusKilledByTo(insn.`var`)

                    val prevInsn = insn.previous
                    if(prevInsn is VarInsnNode && prevInsn.opcode in LOAD_OPCODES) {
                        newSet.plus(CopyAssignment(insn.`var`, prevInsn.`var`))
                    } else {
                        newSet
                    }
                }
                insn is IincInsnNode -> set.minusKilledByTo(insn.`var`)
                else -> set
            }
        }

        private fun Set<CopyAssignment>.minusKilledByTo(index: Int): Set<CopyAssignment> {
            return filterTo(mutableSetOf()) { it.src != index && it.dest != index }
        }

        companion object {

            val LOAD_OPCODES = setOf(
                Opcodes.ILOAD,
                Opcodes.LSTORE,
                Opcodes.FLOAD,
                Opcodes.DLOAD,
                Opcodes.ALOAD
            )

            private val STORE_OPCODES = setOf(
                Opcodes.ISTORE,
                Opcodes.LSTORE,
                Opcodes.FSTORE,
                Opcodes.DSTORE,
                Opcodes.ASTORE
            )
        }
    }
}