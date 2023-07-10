package dev.revtools.updater.classifier

import dev.revtools.updater.asm.MethodEntry
import org.objectweb.asm.Opcodes.*

object MethodClassifier : AbstractClassifier<MethodEntry>() {

    override fun init() {
        addRanker(methodType, 10)
        addRanker(accessFlags, 4)
        addRanker(argTypes, 10)
        addRanker(retType, 5)
    }

    private val methodType = ranker("method type") { a, b ->
        val mask = ACC_STATIC or ACC_NATIVE or ACC_ABSTRACT
        val resultA = a.access and mask
        val resultB = b.access and mask
        return@ranker 1 - Integer.bitCount(resultA.xor(resultB)) / 3.0
    }

    private val accessFlags = ranker("access flags") { a, b ->
        val mask = (ACC_PUBLIC or ACC_PROTECTED or ACC_PRIVATE) or ACC_FINAL or ACC_SYNCHRONIZED or ACC_BRIDGE or ACC_VARARGS or ACC_STRICT or ACC_SYNTHETIC
        val resultA = a.access and mask
        val resultB = b.access and mask
        return@ranker 1 - Integer.bitCount(resultA.xor(resultB)) / 8.0
    }

    private val argTypes = ranker("arg types") { a, b ->
        return@ranker ClassifierUtil.compareClassLists(a.argTypes, b.argTypes)
    }

    private val retType = ranker("ret type") { a, b ->
        return@ranker if(ClassifierUtil.isMaybeEqual(a, b)) 1.0 else 0.0
    }
}