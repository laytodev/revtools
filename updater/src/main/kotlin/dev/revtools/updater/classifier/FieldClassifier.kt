package dev.revtools.updater.classifier

import dev.revtools.updater.asm.FieldEntry
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.FieldInsnNode

object FieldClassifier : AbstractClassifier<FieldEntry>() {

    override fun init() {
        addRanker(fieldType, 10)
        addRanker(accessFlags, 4)
        addRanker(type, 10)
        addRanker(parentField, 10)
        addRanker(childFields, 3)
        addRanker(writeRefs, 6)
        addRanker(readRefs, 6)
        addRanker(initValue, 7)
        addRanker(initIndex, 7)
        addRanker(initCode, 12)
        //addRanker(writeRefsBci, 6)
        //addRanker(readRefsBci, 6)
    }

    private val fieldType = ranker("field type") { a, b ->
        val mask = ACC_STATIC
        val resultA = a.access and mask
        val resultB = b.access and mask
        return@ranker 1 - Integer.bitCount(resultA.xor(resultB)) / 1.0
    }

    private val accessFlags = ranker("access flags") { a, b ->
        val mask = (ACC_PUBLIC or ACC_PROTECTED or ACC_PRIVATE) or ACC_FINAL or ACC_VOLATILE or ACC_TRANSIENT or ACC_SYNTHETIC
        val resultA = a.access and mask
        val resultB = b.access and mask
        return@ranker 1 - Integer.bitCount(resultA.xor(resultB)) / 6.0
    }

    private val type = ranker("type") { a, b ->
        return@ranker if(ClassifierUtil.isMaybeEqual(a.type, b.type)) 1.0 else 0.0
    }

    private val parentField = ranker("parent field") { a, b ->
        return@ranker if(ClassifierUtil.isMaybeEqualNullable(a.parent, b.parent)) 1.0 else 0.0
    }

    private val childFields = ranker("child fields") { a, b ->
        return@ranker ClassifierUtil.compareFieldSets(a.children, b.children)
    }

    private val writeRefs = ranker("write refs") { a, b ->
        return@ranker ClassifierUtil.compareMethodSets(a.writeRefs, b.writeRefs)
    }

    private val readRefs = ranker("read refs") { a, b ->
        return@ranker ClassifierUtil.compareMethodSets(a.readRefs, b.readRefs)
    }

    private val initValue = ranker("init value") { a, b ->
        val valueA = a.value
        val valueB = b.value

        if(valueA == null && valueB == null) return@ranker 1.0
        if(valueA == null || valueB == null) return@ranker 0.0

        return@ranker if(valueA == valueB) 1.0 else 0.0
    }

    private val initCode = ranker("init code") { a, b ->
        val initA = a.initializer
        val initB = b.initializer

        if(initA.isEmpty() && initB.isEmpty()) return@ranker 1.0
        if(initA.isEmpty() || initB.isEmpty()) return@ranker 0.0

        return@ranker ClassifierUtil.compareInsns(initA, initB)
    }

    private val initIndex = ranker("init index") { a, b ->
        val clinitA = a.cls.getMethod("<clinit>", "()V")
        val clinitB = b.cls.getMethod("<clinit>", "()V")

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

        val indexA = fieldsA.indexOf(a)
        val indexB = fieldsB.indexOf(b)

        return@ranker if(indexA == indexB) 1.0 else 0.0
    }

    private val writeRefsBci = ranker("write refs (bci)") { a, b ->
        val ownerA = a.cls.name
        val nameA = a.name
        val descA = a.desc
        val ownerB = b.cls.name
        val nameB = b.name
        val descB = b.desc

        var matched = 0
        var mismatched = 0

        for(src in a.writeRefs) {
            val dst = src.match
            if(dst == null || !b.writeRefs.contains(dst)) {
                mismatched++
                continue
            }

            val map = ClassifierUtil.mapInsns(src, dst)

            val insnsA = src.instructions
            val insnsB = dst.instructions

            for(srcIdx in map.indices) {
                if(map[srcIdx] < 0) continue

                var insn = insnsA[srcIdx]
                if(insn.opcode != PUTFIELD && insn.opcode != PUTSTATIC) continue

                insn as FieldInsnNode
                if(!isSameField(insn, ownerA, nameA, descA, a)) continue

                insn = insnsB[map[srcIdx]]
                insn as FieldInsnNode

                if(!isSameField(insn, ownerB, nameB, descB, b)) {
                    mismatched++
                } else {
                    matched++
                }
            }
        }

        if(matched == 0 && mismatched == 0) {
            return@ranker 1.0
        } else {
            return@ranker matched.toDouble() / (matched + mismatched)
        }
    }

    private val readRefsBci = ranker("read refs (bci)") { a, b ->
        val ownerA = a.cls.name
        val nameA = a.name
        val descA = a.desc
        val ownerB = b.cls.name
        val nameB = b.name
        val descB = b.desc

        var matched = 0
        var mismatched = 0

        for(src in a.readRefs) {
            val dst = src.match
            if(dst == null || !b.readRefs.contains(dst)) {
                mismatched++
                continue
            }

            val map = ClassifierUtil.mapInsns(src, dst)

            val insnsA = src.instructions
            val insnsB = dst.instructions

            for(srcIdx in map.indices) {
                if(map[srcIdx] < 0) continue

                var insn = insnsA[srcIdx]
                if(insn.opcode != GETFIELD && insn.opcode != GETSTATIC) continue

                insn as FieldInsnNode
                if(!isSameField(insn, ownerA, nameA, descA, a)) continue

                insn = insnsB[map[srcIdx]]
                insn as FieldInsnNode

                if(!isSameField(insn, ownerB, nameB, descB, b)) {
                    mismatched++
                } else {
                    matched++
                }
            }
        }

        if(matched == 0 && mismatched == 0) {
            return@ranker 1.0
        } else {
            return@ranker matched.toDouble() / (matched + mismatched)
        }
    }

    private fun isSameField(insn: FieldInsnNode, owner: String, name: String, desc: String, field: FieldEntry): Boolean {
        val target = field.group.getClass(insn.owner)
        return insn.name == name && insn.desc == desc
                && (insn.owner == owner || target != null && target.resolveField(name, desc) == field)
    }
}