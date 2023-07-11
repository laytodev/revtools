package dev.revtools.updater.classifier

import dev.revtools.updater.asm.ClassEntry
import dev.revtools.updater.asm.FieldEntry
import dev.revtools.updater.asm.MethodEntry
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.FieldInsnNode

object ClassClassifier : AbstractClassifier<ClassEntry>() {

    override fun init() {
        addRanker(classType, 20)
        addRanker(superClass, 4)
        addRanker(childClasses, 3)
        addRanker(interfaces, 3)
        addRanker(implementers, 2)
        addRanker(hierarchyDepth, 1)
        addRanker(hierarchySiblings, 2)
        addRanker(outerClass, 6)
        addRanker(innerClasses, 5)
        addRanker(memberMethodCount, 3)
        addRanker(memberFieldCount, 3)
        addRanker(staticMethodCount, 3)
        addRanker(staticFieldCount, 3)
        addRanker(outRefs, 6)
        addRanker(inRefs, 6)
        addRanker(methodOutRefs, 5)
        addRanker(methodInRefs, 6)
        addRanker(fieldWriteRefs, 5)
        addRanker(fieldReadRefs, 5)
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

    private val outerClass = ranker("outer class") { a, b ->
        val outerA = a.outerClass
        val outerB = b.outerClass

        if(outerA == null && outerB == null) return@ranker 1.0
        if(outerA == null || outerB == null) return@ranker 0.0

        return@ranker if(ClassifierUtil.isMaybeEqual(outerA, outerB)) 1.0 else 0.0
    }

    private val innerClasses = ranker("inner classes") { a, b ->
        val innerA = a.innerClasses
        val innerB = b.innerClasses

        if(innerA.isEmpty() && innerB.isEmpty()) return@ranker 1.0
        if(innerA.isEmpty() || innerB.isEmpty()) return@ranker 0.0

        return@ranker ClassifierUtil.compareClassSets(innerA, innerB)
    }

    private val memberMethodCount = ranker("member method count") { a, b ->
        return@ranker ClassifierUtil.compareCounts(a.memberMethods.size, b.memberMethods.size)
    }

    private val memberFieldCount = ranker("member field count") { a, b ->
        return@ranker ClassifierUtil.compareCounts(a.memberFields.size, b.memberFields.size)
    }

    private val staticMethodCount = ranker("static method count") { a, b ->
        return@ranker ClassifierUtil.compareCounts(a.staticMethods.size, b.staticMethods.size)
    }

    private val staticFieldCount = ranker("static field count") { a, b ->
        return@ranker ClassifierUtil.compareCounts(a.staticFields.size, b.staticFields.size)
    }

    private val hierarchyDepth = ranker("hierarchy depth") { a, b ->
        var countA = 0
        var countB = 0

        var clsA = a
        var clsB = b
        while(clsA.superClass != null) {
            clsA = clsA.superClass!!
            countA++
        }
        while(clsB.superClass != null) {
            clsB = clsB.superClass!!
            countB++
        }

        return@ranker ClassifierUtil.compareCounts(countA, countB)
    }

    private val hierarchySiblings = ranker("hierarchy siblings") { a, b ->
        return@ranker ClassifierUtil.compareCounts(a.superClass?.childClasses?.size ?: 0, b.superClass?.childClasses?.size ?: 0)
    }

    private val outRefs = ranker("out refs") { a, b ->
        val refsA = a.outRefs
        val refsB = b.outRefs
        return@ranker ClassifierUtil.compareClassSets(refsA, refsB)
    }

    private val ClassEntry.outRefs: HashSet<ClassEntry> get() {
        val ret = hashSetOf<ClassEntry>()
        methods.forEach { ret.addAll(it.classRefs) }
        fields.forEach { ret.add(it.type) }
        return ret
    }

    private val inRefs = ranker("in refs") { a, b ->
        val refsA = a.inRefs
        val refsB = b.inRefs
        return@ranker ClassifierUtil.compareClassSets(refsA, refsB)
    }

    private val ClassEntry.inRefs: HashSet<ClassEntry> get() {
        val ret = hashSetOf<ClassEntry>()
        methodTypeRefs.forEach { ret.add(it.cls) }
        fieldTypeRefs.forEach { ret.add(it.cls) }
        return ret
    }

    private val methodOutRefs = ranker("method out refs") { a, b ->
        val refsA = a.methodOutRefs
        val refsB = b.methodOutRefs
        return@ranker ClassifierUtil.compareMethodSets(refsA, refsB)
    }

    private val ClassEntry.methodOutRefs: HashSet<MethodEntry> get() {
        val ret = hashSetOf<MethodEntry>()
        methods.forEach { ret.addAll(it.refsOut) }
        return ret
    }

    private val methodInRefs = ranker("method in refs") { a, b ->
        val refsA = a.methodInRefs
        val refsB = b.methodInRefs
        return@ranker ClassifierUtil.compareMethodSets(refsA, refsB)
    }

    private val ClassEntry.methodInRefs: HashSet<MethodEntry> get() {
        val ret = hashSetOf<MethodEntry>()
        methods.forEach { ret.addAll(it.refsIn) }
        return ret
    }
    
    private val fieldWriteRefs = ranker("field write refs") { a, b ->
        val refsA = a.fieldWriteRefs
        val refsB = b.fieldWriteRefs
        return@ranker ClassifierUtil.compareFieldSets(refsA, refsB)
    }
    
    private val ClassEntry.fieldWriteRefs: HashSet<FieldEntry> get() {
        val ret = hashSetOf<FieldEntry>()
        methods.forEach { ret.addAll(it.fieldWriteRefs) }
        return ret
    }

    private val fieldReadRefs = ranker("field read refs") { a, b ->
        val refsA = a.fieldReadRefs
        val refsB = b.fieldReadRefs
        return@ranker ClassifierUtil.compareFieldSets(refsA, refsB)
    }

    private val ClassEntry.fieldReadRefs: HashSet<FieldEntry> get() {
        val ret = hashSetOf<FieldEntry>()
        methods.forEach { ret.addAll(it.fieldReadRefs) }
        return ret
    }

    private val clinitFieldOrder = ranker("clinit field order") { a, b ->
        val clinitA = a.getMethod("<clinit>", "()V")
        val clinitB = b.getMethod("<clinit>", "()V")

        if(clinitA == null && clinitB == null) return@ranker 1.0
        if(clinitA == null || clinitB == null) return@ranker 0.0

        val fieldsA = mutableListOf<FieldEntry>()
        val fieldsB = mutableListOf<FieldEntry>()

        clinitA.instructions.filter { it.opcode == PUTSTATIC }.forEach { insn ->
            insn as FieldInsnNode
            val owner = clinitA.group.getClass(insn.owner) ?: return@forEach
            val dst = owner.resolveField(insn.name, insn.desc) ?: return@forEach
            fieldsA.add(dst)
        }

        clinitB.instructions.filter { it.opcode == PUTSTATIC }.forEach { insn ->
            insn as FieldInsnNode
            val owner = clinitB.group.getClass(insn.owner) ?: return@forEach
            val dst = owner.resolveField(insn.name, insn.desc) ?: return@forEach
            fieldsB.add(dst)
        }

        return@ranker ClassifierUtil.compareFieldLists(fieldsA, fieldsB)
    }
}