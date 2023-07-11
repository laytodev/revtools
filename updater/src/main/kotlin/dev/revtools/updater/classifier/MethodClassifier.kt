package dev.revtools.updater.classifier

import dev.revtools.updater.asm.ClassEntry
import dev.revtools.updater.asm.MethodEntry
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.AbstractInsnNode.INVOKE_DYNAMIC_INSN
import org.objectweb.asm.tree.AbstractInsnNode.METHOD_INSN
import org.objectweb.asm.tree.MethodInsnNode

object MethodClassifier : AbstractClassifier<MethodEntry>() {

    override fun init() {
        addRanker(methodType, 10)
        addRanker(accessFlags, 4)
        addRanker(argTypes, 10)
        addRanker(retType, 5)
        addRanker(stringConstants, 5)
        addRanker(parentMethod, 5)
        addRanker(childMethods, 3)
        addRanker(classRefs, 3)
        addRanker(outRefs, 6)
        addRanker(inRefs, 6)
        addRanker(fieldWrites, 5)
        addRanker(fieldReads, 5)
        addRanker(code, 12)
        addRanker(inRefsBci, 6)
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

    private val classRefs = ranker("class refs") { a, b ->
        return@ranker ClassifierUtil.compareClassSets(a.classRefs, b.classRefs)
    }

    private val parentMethod = ranker("parent method") { a, b ->
        return@ranker if(ClassifierUtil.isMaybeEqualNullable(a.parent, b.parent)) 1.0 else 0.0
    }

    private val childMethods = ranker("child methods") { a, b ->
        return@ranker ClassifierUtil.compareMethodSets(a.children, b.children)
    }

    private val outRefs = ranker("out refs") { a, b ->
        return@ranker ClassifierUtil.compareMethodSets(a.refsOut, b.refsOut)
    }

    private val inRefs = ranker("in refs") { a, b ->
        return@ranker ClassifierUtil.compareMethodSets(a.refsIn, b.refsIn)
    }

    private val fieldWrites = ranker("field writes") { a, b ->
        return@ranker ClassifierUtil.compareFieldSets(a.fieldWriteRefs, b.fieldWriteRefs)
    }

    private val fieldReads = ranker("field reads") { a, b ->
        return@ranker ClassifierUtil.compareFieldSets(a.fieldReadRefs, b.fieldReadRefs)
    }

    private val code = ranker("code") { a, b ->
        return@ranker ClassifierUtil.compareInsns(a.instructions, b.instructions)
    }

    private val stringConstants = ranker("string constants") { a, b ->
        val stringsA = hashSetOf<String>()
        val stringsB = hashSetOf<String>()

        ClassifierUtil.extractStrings(a.instructions, stringsA)
        ClassifierUtil.extractStrings(b.instructions, stringsB)

        return@ranker ClassifierUtil.compareSets(stringsA, stringsB)
    }

    private val inRefsBci = ranker("in refs (bci)") { a, b ->
        val ownerA = a.cls.name
        val nameA = a.name
        val descA = a.desc
        val ownerB = b.cls.name
        val nameB = b.name
        val descB = b.desc

        var matched = 0
        var mismatched = 0

        for(src in a.refsIn) {
            if(src == a) continue

            val dst = src.match
            if(dst == null || !b.refsIn.contains(dst)) {
                mismatched++
                continue
            }

            val map = ClassifierUtil.mapInsns(src, dst) ?: continue

            val insnsA = src.instructions
            val insnsB = dst.instructions

            for(srcIdx in map.indices) {
                if(map[srcIdx] < 0) continue
                var insn = insnsA[srcIdx]

                val type = insn.type
                if(type != METHOD_INSN && type != INVOKE_DYNAMIC_INSN) continue
                if(!isSameMethod(insn, ownerA, nameA, descA, a)) continue

                insn = insnsB[map[srcIdx]]
                if(!isSameMethod(insn, ownerB, nameB, descB, b)) {
                    mismatched++
                } else {
                    matched++
                }
            }
        }

        return@ranker if(matched == 0 && mismatched == 0) 1.0 else matched.toDouble() / (matched + mismatched)
    }

    private fun isSameMethod(insn: AbstractInsnNode, owner: String, name: String, desc: String, method: MethodEntry): Boolean {
        insn as MethodInsnNode
        val sOwner = insn.owner
        val sName = insn.name
        val sDesc = insn.desc
        val sItf = insn.itf

        val target: ClassEntry?
        return sName == name
                && sDesc == desc
                && (sOwner == owner) || (method.group.getClass(sOwner).also { target = it } != null && target!!.resolveMethod(name, desc, sItf) == method)
    }
}