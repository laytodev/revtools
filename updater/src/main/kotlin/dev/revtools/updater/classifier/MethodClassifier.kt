package dev.revtools.updater.classifier

import dev.revtools.updater.asm.MethodInstance
import org.objectweb.asm.Opcodes.*

object MethodClassifier : AbstractClassifier<MethodInstance>() {

    override fun init() {
        addRanker(methodType, weight = 10)
    }

    private val methodType = ranker("method type") { a, b ->
        val mask = ACC_STATIC or ACC_NATIVE or ACC_ABSTRACT
        val resA = a.access and mask
        val resB = b.access and mask
        return@ranker 1 - Integer.bitCount(resA.xor(resB)) / 3.0
    }
}