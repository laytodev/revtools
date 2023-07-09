package dev.revtools.deobfuscator.asm.analysis

import dev.revtools.deobfuscator.asm.util.UniqueQueue
import org.jgrapht.Graph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.EdgeReversedGraph
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.MethodNode

abstract class DataFlowAnalyzer<T>(private val method: MethodNode, private val backwards: Boolean = false) {

    private val graph: Graph<Int, DefaultEdge> = ControlFlowAnalyzer.create(method).let {
        if(backwards) EdgeReversedGraph(it)
        else it
    }

    private val inSets = mutableMapOf<Int, T>()
    private val outSets = mutableMapOf<Int, T>()

    open fun createEntrySet(): T = createInitialSet()

    abstract fun createInitialSet(): T
    abstract fun join(set1: T, set2: T): T
    abstract fun transfer(set: T, insn: AbstractInsnNode): T

    fun getInSet(index: Int): T? = inSets[index]
    fun getInSet(insn: AbstractInsnNode): T? = getInSet(method.instructions.indexOf(insn))

    fun getOutSet(index: Int): T? = outSets[index]
    fun getOutSet(insn: AbstractInsnNode): T? = getOutSet(method.instructions.indexOf(insn))

    fun analyze() {
        val entrySet = createEntrySet()
        val initialSet = createInitialSet()

        val queue = UniqueQueue<Int>()
        queue += graph.vertexSet().filter { graph.inDegreeOf(it) == 0 }

        while(true) {
            val node = queue.removeFirstOrNull() ?: break

            val predecessors = graph.incomingEdgesOf(node).map { edge ->
                outSets[graph.getEdgeSource(edge)] ?: initialSet
            }

            val inSet = if(predecessors.isEmpty()) entrySet else predecessors.reduce(this::join)
            inSets[node] = inSet

            val outSet = transfer(inSet, method.instructions[node])

            if(outSets[node] != outSet) {
                outSets[node] = outSet
                for(edge in graph.outgoingEdgesOf(node)) {
                    val successor = graph.getEdgeTarget(edge)
                    queue += successor
                }
            }
        }
    }
}