package dev.revtools.deobfuscator.transformer

import dev.revtools.deobfuscator.Transformer
import dev.revtools.deobfuscator.asm.LabelMap
import dev.revtools.deobfuscator.asm.tree.ClassGroup
import dev.revtools.deobfuscator.asm.tree.owner
import org.objectweb.asm.tree.AbstractInsnNode.*
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicInterpreter
import org.objectweb.asm.tree.analysis.BasicValue
import org.tinylog.kotlin.Logger
import java.util.Stack

class ControlFlowFixer : Transformer {

    private var count = 0

    override fun run(group: ClassGroup) {
        group.classes.forEach { cls ->
            cls.methods.filter { it.tryCatchBlocks.isEmpty() }.forEach { method ->
                val cfg = ControlFlowAnalyzer(method)

                /*
                 * Rebuild method instructions
                 */
                val origInsns = method.instructions
                val insns = InsnList()

                if(cfg.blocks.isNotEmpty()) {
                    val labelMap = LabelMap()

                    val queue = Stack<Block>()
                    val visited = hashSetOf<Block>()
                    queue.push(cfg.blocks.first())

                    while(queue.isNotEmpty()) {
                        val block = queue.pop()
                        if(block in visited) continue
                        visited.add(block)
                        block.successors.forEach { queue.push(it.origin) }
                        block.next?.also { queue.push(it) }
                        for(i in block.start until block.end) {
                            insns.add(origInsns[i].clone(labelMap))
                        }
                    }
                } else {
                    insns.add(origInsns)
                }

                method.instructions = insns
                count += cfg.blocks.size
            }
        }

        Logger.info("Reordered $count method control-flow blocks.")
    }

    private class ControlFlowAnalyzer(private val method: MethodNode) : Analyzer<BasicValue>(BasicInterpreter()) {

        val blocks = mutableListOf<Block>()

        init {
            analyze(method.owner.name, method)
        }

        override fun init(owner: String, method: MethodNode) {
            val insns = method.instructions
            var block = Block()
            blocks.add(block)
            for(i in 0 until insns.size()) {
                val insn = insns[i]
                block.end++
                if(insn.next == null) break
                if(insn.next.type == LABEL || insn.type in arrayOf(JUMP_INSN, LOOKUPSWITCH_INSN, TABLESWITCH_INSN)) {
                    block = Block()
                    block.start = i + 1
                    block.end = i + 1
                    blocks.add(block)
                }
            }
        }

        override fun newControlFlowEdge(insnIndex: Int, successorIndex: Int) {
            val block1 = blocks.first { insnIndex in it.start until it.end }
            val block2 = blocks.first { successorIndex in it.start until it.end }
            if(block1 != block2) {
                if(insnIndex + 1 == successorIndex) {
                    block1.next = block2
                    block2.prev = block1
                } else {
                    block1.successors.add(block2)
                }
            }
        }
    }

    private class Block {

        var start = 0
        var end = 0

        var prev: Block? = null
        var next: Block? = null

        val successors = mutableListOf<Block>()
        val predecessors = mutableListOf<Block>()

        val origin: Block get() {
            var cur = this
            var last = prev
            while(last != null) {
                cur = last
                last = cur.prev
            }
            return cur
        }
    }
}