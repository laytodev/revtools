@file:Suppress("RedundantIf")

package dev.revtools.updater.classifier

import dev.revtools.updater.asm.ClassInstance
import dev.revtools.updater.asm.FieldInstance
import dev.revtools.updater.asm.Matchable
import dev.revtools.updater.asm.MethodInstance
import dev.revtools.updater.util.identityHashSetOf
import kotlin.math.abs
import kotlin.math.max

object ClassifierUtil {

    fun isPotentiallyEqual(a: ClassInstance, b: ClassInstance): Boolean {
        if(a == b) return true
        if(a.hasMatch()) return a.match == b
        if(b.hasMatch()) return b.match == a
        if(a.parent != null || b.parent != null) { if(!isPotentiallyEqualNullable(a.parent, b.parent)) return false }
        if(a.interfaces.size != b.interfaces.size) return false
        return true
    }

    fun isPotentiallyEqual(a: MethodInstance, b: MethodInstance): Boolean {
        if(a == b) return true
        if(a.hasMatch()) return a.match == b
        if(b.hasMatch()) return b.match == a
        if(!a.isStatic() && !b.isStatic()) { if(!isPotentiallyEqual(a.cls, b.cls)) return false }
        if((a.name.startsWith("<") || b.name.startsWith("<")) && a.name != b.name) return false
        return true
    }

    fun isPotentiallyEqual(a: FieldInstance, b: FieldInstance): Boolean {
        if(a == b) return true
        if(a.hasMatch()) return a.match == b
        if(b.hasMatch()) return b.match == a
        if(!a.isStatic() && !b.isStatic()) { if(!isPotentiallyEqual(a.cls, b.cls)) return false }
        return true
    }

    fun isPotentiallyEqualNullable(a: ClassInstance?, b: ClassInstance?): Boolean {
        if(a == null && b == null) return true
        if(a == null || b == null) return false
        return isPotentiallyEqual(a, b)
    }

    fun isPotentiallyEqualNullable(a: MethodInstance?, b: MethodInstance?): Boolean {
        if(a == null && b == null) return true
        if(a == null || b == null) return false
        return isPotentiallyEqual(a, b)
    }

    fun isPotentiallyEqualNullable(a: FieldInstance?, b: FieldInstance?): Boolean {
        if(a == null && b == null) return true
        if(a == null || b == null) return false
        return isPotentiallyEqual(a, b)
    }

    fun Set<MethodInstance>.filterPotentialMatches(method: MethodInstance): Set<MethodInstance> {
        val results = identityHashSetOf<MethodInstance>()
        this.forEach { m ->
            if(isMethodTypePotentiallyEqual(method, m)) {
                results.add(m)
            }
        }
        return results
    }

    private fun isMethodTypePotentiallyEqual(a: MethodInstance, b: MethodInstance): Boolean {
        if(a.argTypes.size != b.argTypes.size) return false
        if(!isPotentiallyEqual(a.retType, b.retType)) return false
        for(i in 0 until a.argTypes.size) {
            val argA = a.argTypes[i]
            val argB = b.argTypes[i]
            if(!isPotentiallyEqual(argA, argB)) return false
        }
        return true
    }

    private fun ClassInstance.isPrimitive(): Boolean {
        return this.name in arrayOf("Z", "B", "S", "C", "I", "V", "J", "F", "D")
    }

    private fun ClassInstance.isArray() = name.startsWith("[")

    /**
     * === COMPARE FUNCTIONS ===
     */

    fun compareCounts(a: Int, b: Int): Double {
        val delta = abs(a - b)
        if(delta == 0) return 1.0
        return 1 - delta.toDouble() / max(a, b)
    }

    fun <T> compareSets(a: Set<T>, b: Set<T>): Double {
        val setA = mutableSetOf<T>().also { it.addAll(a) }
        val setB = mutableSetOf<T>().also { it.addAll(b) }

        val oldSize = setB.size
        setB.removeAll(setA)

        val matched = oldSize - setB.size
        val total = setA.size - matched + oldSize

        return if(total == 0) 1.0 else matched.toDouble() / total
    }

    fun compareClassSets(a: Set<ClassInstance>, b: Set<ClassInstance>): Double {
        return compareIdentitySets(a, b, ClassifierUtil::isPotentiallyEqual)
    }

    fun compareMethodSets(a: Set<MethodInstance>, b: Set<MethodInstance>): Double {
        return compareIdentitySets(a, b, ClassifierUtil::isPotentiallyEqual)
    }

    fun compareFieldSets(a: Set<FieldInstance>, b: Set<FieldInstance>): Double {
        return compareIdentitySets(a, b, ClassifierUtil::isPotentiallyEqual)
    }

    private fun <T : Matchable<T>> compareIdentitySets(a: Set<T>, b: Set<T>, compare: (T, T) -> Boolean): Double {
        val setA = identityHashSetOf<T>().also { it.addAll(a) }
        val setB = identityHashSetOf<T>().also { it.addAll(b) }

        if(setA.isEmpty() || setB.isEmpty()) {
            return if(setA.isEmpty() && setB.isEmpty()) 1.0 else 0.0
        }

        val total = setA.size + setB.size
        var unmatched = 0

        var itr = setA.iterator()
        while(itr.hasNext()) {
            val entryA = itr.next()

            if(setB.remove(entryA)) {
                itr.remove()
            } else if(entryA.hasMatch()) {
                if(!setB.remove(entryA.match)) {
                    unmatched++
                }
                itr.remove()
            }
        }

        itr = setA.iterator()
        while(itr.hasNext()) {
            val entryA = itr.next()
            var found = false
            for(entryB in setB) {
                if(compare(entryA, entryB)) {
                    found = true
                    break
                }
            }

            if(!found) {
                unmatched++
                itr.remove()
            }
        }

        for(entryB in setB) {
            var found = false

            for(entryA in setA) {
                if(compare(entryA, entryB)) {
                    found = true
                    break
                }
            }

            if(!found) {
                unmatched++
            }
        }

        return (total - unmatched).toDouble() / total
    }
}