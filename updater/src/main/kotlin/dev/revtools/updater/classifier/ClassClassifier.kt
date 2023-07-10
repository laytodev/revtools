package dev.revtools.updater.classifier

import dev.revtools.updater.asm.ClassEntry
import org.objectweb.asm.Opcodes.*

object ClassClassifier : AbstractClassifier<ClassEntry>() {

    override fun init() {
        addRanker(classType, 20)
        addRanker(superClass, 4)
        addRanker(childClasses, 3)
        addRanker(interfaces, 3)
        addRanker(implementers, 2)
    }

    private val classType = ranker("class type") { a, b ->
        val mask = ACC_ENUM or ACC_INTERFACE or ACC_ANNOTATION or ACC_RECORD or ACC_ABSTRACT
        val resultA = a.access and mask
        val resultB = b.access and mask
        return@ranker 1 - Integer.bitCount(resultA.xor(resultB)) / 5.0
    }

    private val superClass = ranker("super class") { a, b ->
        if(a.superClass == null && b.superClass == null) return@ranker 1.0
        if(a.superClass == null || b.superClass == null) return@ranker 0.0
        return@ranker if(ClassifierUtil.isMaybeEqual(a.superClass!!, b.superClass!!)) 1.0 else 0.0
    }

    private val childClasses = ranker("child classes") { a, b ->
        return@ranker ClassifierUtil.compareClassSets(a.childClasses, b.childClasses)
    }

    private val interfaces = ranker("interfaces") { a, b ->
        return@ranker ClassifierUtil.compareClassSets(a.interfaces, b.interfaces)
    }

    private val implementers = ranker("implementers") { a, b ->
        return@ranker ClassifierUtil.compareClassSets(a.implementers, b.implementers)
    }

}