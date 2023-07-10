package dev.revtools.updater.classifier

import dev.revtools.updater.asm.ClassEntry
import dev.revtools.updater.asm.FieldEntry
import dev.revtools.updater.asm.Matchable
import dev.revtools.updater.asm.MethodEntry
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.HashSet
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object ClassifierUtil {

    fun isMaybeEqual(a: ClassEntry, b: ClassEntry): Boolean {
        if(a == b) return true
        if(a.hasMatch()) return a.match == b
        if(b.hasMatch()) return b.match == a
        if(a.isArray() != b.isArray()) return false
        return !(a.isArray() && !isMaybeEqual(a.elementClass!!, b.elementClass!!))
    }

    fun isMaybeEqual(a: MethodEntry, b: MethodEntry): Boolean {
        if(a == b) return true
        if(a.hasMatch()) return a.match == b
        if(b.hasMatch()) return b.match == a
        if(!a.isStatic() || !b.isStatic()) {
            if(!isMaybeEqual(a.cls, b.cls)) return false
        }
        return !(a.name.startsWith("<") && b.name.startsWith("<") && a.name != b.name)
    }

    fun isMaybeEqual(a: FieldEntry, b: FieldEntry): Boolean {
        if(a == b) return true
        if(a.hasMatch()) return a.match == b
        if(b.hasMatch()) return b.match == a
        if(!a.isStatic() || !b.isStatic()) {
            if(!isMaybeEqual(a.cls, b.cls)) return false
        }
        return true
    }

    private fun <T : Matchable<T>> rank(src: T, dst: T, rankers: List<Ranker<T>>, maybeEqualCheck: (T, T) -> Boolean, maxMismatch: Double): RankResult<T>? {
        if(!maybeEqualCheck(src, dst)) return null

        var score = 0.0
        var mismatch = 0.0

        rankers.forEach { ranker ->
            val cScore = ranker.getScore(src, dst)
            val weight = ranker.weight
            val weightedScore = cScore * weight
            mismatch += weight - weightedScore
            if(mismatch >= maxMismatch) return null
            score += weightedScore
        }

        return RankResult(dst, score)
    }

    fun <T : Matchable<T>> rank(src: T, dsts: List<T>, rankers: List<Ranker<T>>, maybeEqualCheck: (T, T) -> Boolean, maxMismatch: Double): List<RankResult<T>> {
        val results = mutableListOf<RankResult<T>>()
        for(dst in dsts) {
            val result = rank(src, dst, rankers, maybeEqualCheck, maxMismatch)
            if(result != null) results.add(result)
        }
        return results.sortedByDescending { it.score }
    }

    fun <T : Matchable<T>> rankParallel(src: T, dsts: List<T>, rankers: List<Ranker<T>>, maybeEqualCheck: (T, T) -> Boolean, maxMismatch: Double): List<RankResult<T>> {
        val executor = Executors.newWorkStealingPool()
        val results = mutableListOf<RankResult<T>>()
        dsts.forEach { dst ->
            executor.execute {
                val result = rank(src, dst, rankers, maybeEqualCheck, maxMismatch)
                if(result != null) results.add(result)
            }
        }
        return results.sortedByDescending { it.score }
    }

    fun compareCounts(a: Int, b: Int): Double {
        val delta = abs(a - b)
        if(delta == 0) return 1.0
        return 1.0 - delta.toDouble() / max(a, b)
    }

    fun <T> compareSets(a: HashSet<T>, b: HashSet<T>): Double {
        val setA = hashSetOf<T>().also { it.addAll(a) }
        val setB = hashSetOf<T>().also { it.addAll(b) }

        val oldSize = setB.size
        setB.removeAll(setA)


        val matched = oldSize - setB.size
        val total = setA.size - matched + oldSize

        return if (total == 0) 1.0 else matched.toDouble() / total
    }

    fun compareClassSets(a: HashSet<ClassEntry>, b: HashSet<ClassEntry>): Double {
        return compareMatchableSets(a, b, ClassifierUtil::isMaybeEqual)
    }

    fun compareMethodSets(a: HashSet<MethodEntry>, b: HashSet<MethodEntry>): Double {
        return compareMatchableSets(a, b, ClassifierUtil::isMaybeEqual)
    }

    fun compareFieldSets(a: HashSet<FieldEntry>, b: HashSet<FieldEntry>): Double {
        return compareMatchableSets(a, b, ClassifierUtil::isMaybeEqual)
    }

    private fun <T : Matchable<T>> compareMatchableSets(a: HashSet<T>, b: HashSet<T>, compare: (a: T, b: T) -> Boolean): Double {
        if(a.isEmpty() || b.isEmpty()) {
            return if(a.isEmpty() && b.isEmpty()) 1.0 else 0.0
        }

        val setA = hashSetOf<T>().also { it.addAll(a) }
        val setB = hashSetOf<T>().also { it.addAll(b) }

        val total = setA.size + setB.size
        var unmatched = 0

        val itr1 = setA.iterator()
        while(itr1.hasNext()) {
            val entryA = itr1.next()
            if(setB.remove(entryA)) {
                itr1.remove()
            } else if(entryA.hasMatch()) {
                if(!setB.remove(entryA.match)) {
                    unmatched++
                }
                itr1.remove()
            }
        }

        val itr2 = setA.iterator()
        while(itr2.hasNext()) {
            val entryA = itr2.next()
            var found = false
            for(entryB in setB) {
                if(compare(entryA, entryB)) {
                    found = true
                    break
                }
            }
            if(!found) {
                unmatched++
                itr2.remove()
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

    fun compareClassLists(listA: List<ClassEntry>, listB: List<ClassEntry>): Double {
        return compareLists(listA, listB, List<ClassEntry>::get, List<ClassEntry>::size) { a: ClassEntry, b: ClassEntry ->
            if (isMaybeEqual(a, b)) COMPARED_SIMILAR else COMPARED_DISTINCT
        }
    }

    private fun <T, U> compareLists(listA: T, listB: T, getElement: (T, Int) -> U, getSize: (T) -> Int, compareElement: (a: U, b: U) -> Int): Double {
        val sizeA = getSize(listA)
        val sizeB = getSize(listB)

        if(sizeA == 0 && sizeB == 0) return 1.0
        if(sizeA == 0 || sizeB == 0) return 0.0

        if(sizeA == sizeB) {
            var match = true
            for(i in 0 until sizeA) {
                if(compareElement(getElement(listA, i), getElement(listB, i)) != COMPARED_SIMILAR) {
                    match = false
                    break
                }
            }

            if(match) return 1.0
        }

        val v0 = IntArray(sizeB + 1)
        val v1 = IntArray(sizeB + 1)

        for(i in 1 until v0.size) {
            v0[i] = i * COMPARED_DISTINCT
        }

        for(i in 0 until sizeA) {
            v1[0] = (i + 1) * COMPARED_DISTINCT

            for(j in 0 until sizeB) {
                val cost = compareElement(getElement(listA, i), getElement(listB, j))
                v1[j + 1] = min(min(v1[j] + COMPARED_DISTINCT, v0[j + 1] + COMPARED_DISTINCT), v0[j] + cost)
            }

            for(j in v0.indices) {
                v0[j] = v1[j]
            }
        }

        val distance = v1[sizeB]
        val upperBound = max(sizeA, sizeB) * COMPARED_DISTINCT

        return 1 - distance.toDouble() / upperBound
    }

    private fun <T, U> mapLists(listA: T, listB: T, getElement: (T, Int) -> U, getSize: (T) -> Int, compareElement: (a: U, b: U) -> Int): IntArray {
        val sizeA = getSize(listA)
        val sizeB = getSize(listB)

        if(sizeA == 0 && sizeB == 0) return intArrayOf()

        val ret = IntArray(sizeA)

        if(sizeA == 0 || sizeB == 0) {
            Arrays.fill(ret, -1)
            return ret
        }

        if(sizeA == sizeB) {
            var match = true
            for(i in 0 until sizeA) {
                if(compareElement(getElement(listA, i), getElement(listB, i)) != COMPARED_SIMILAR) {
                    match = false
                    break
                }
            }

            if(match) {
                for(i in ret.indices) {
                    ret[i] = i
                }
                return ret
            }
        }

        val size = sizeA + 1
        val v = IntArray(size * (sizeB + 1))

        for(i in 1 .. sizeA) {
            v[i + 0] = i * COMPARED_DISTINCT
        }

        for(j in 1 .. sizeB) {
            v[0 + j * size] = j * COMPARED_DISTINCT
        }

        for(j in 1 .. sizeB) {
            for(i in 1 .. sizeA) {
                val cost = compareElement(getElement(listA, i - 1), getElement(listB, j - 1))
                v[i + j * size] = min(min(v[i - 1 + j * size] + COMPARED_DISTINCT,
                    v[i + (j - 1) * size] + COMPARED_DISTINCT),
                    v[i - 1 + (j - 1) * size] + cost)
            }
        }

        var i = sizeA
        var j = sizeB

        while(i > 0 || j > 0) {
            val c = v[i + j * size]
            val delCost = if(i > 0) v[i - 1 + j * size] else Int.MAX_VALUE
            val insCost = if(j > 0) v[i + (j - 1) * size] else Int.MAX_VALUE
            val keepCost = if(j > 0 && i > 0) v[i - 1 + (j - 1) * size] else Int.MAX_VALUE

            if(keepCost <= delCost && keepCost <= insCost) {
                if(c - keepCost >= COMPARED_DISTINCT) {
                    ret[i - 1] = -1
                } else {
                    ret[i - 1] = j - 1
                }
                i--
                j--
            } else if(delCost < insCost) {
                ret[i - 1] = -1
                i--
            } else {
                j--
            }
        }

        return ret
    }

    const val COMPARED_SIMILAR = 0
    const val COMPARED_POSSIBLE = 1
    const val COMPARED_DISTINCT = 2
}