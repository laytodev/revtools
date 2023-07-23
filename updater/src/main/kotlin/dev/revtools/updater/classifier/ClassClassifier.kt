package dev.revtools.updater.classifier

import dev.revtools.updater.asm.ClassInstance
import org.objectweb.asm.Opcodes.*

object ClassClassifier : AbstractClassifier<ClassInstance>() {

    override fun init() {
        addRanker(classType, weight = 20)
        addRanker(parentClass, weight = 4)
        addRanker(children, weight = 3)
        addRanker(interfaces, weight = 3)
        addRanker(implementers, weight = 2)
    }

    private val classType = ranker("class type") { a, b ->
        val mask = ACC_ENUM or ACC_INTERFACE or ACC_ANNOTATION or ACC_RECORD or ACC_ABSTRACT
        val resA = a.access and mask
        val resB = b.access and mask
        return@ranker 1 - Integer.bitCount(resA.xor(resB)) / 5.0
    }

    private val parentClass = ranker("parent class") { a, b ->
        if(a.parent == null && b.parent == null) return@ranker 1.0
        if(a.parent == null || b.parent == null) return@ranker 0.0
        return@ranker if(ClassifierUtil.isPotentiallyEqual(a.parent!!, b.parent!!)) 1.0 else 0.0
    }

    private val children = ranker("children") { a, b ->
        return@ranker ClassifierUtil.compareClassSets(a.children, b.children)
    }

    private val interfaces = ranker("interfaces") { a, b ->
        return@ranker ClassifierUtil.compareClassSets(a.interfaces, b.interfaces)
    }

    private val implementers = ranker("implementers") { a, b ->
        return@ranker ClassifierUtil.compareClassSets(a.implementers, b.implementers)
    }
}