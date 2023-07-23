package dev.revtools.updater.classifier

import dev.revtools.updater.asm.Matchable

abstract class AbstractClassifier<T : Matchable<T>> {

    private val rankers = mutableMapOf<RankerLevel, MutableList<Ranker<T>>>()
    private var maxScore = mutableMapOf<RankerLevel, Double>()

    abstract fun init()

    fun addRanker(ranker: Ranker<T>, weight: Int, vararg levels: RankerLevel) {
        val lvls = if(levels.isEmpty()) RankerLevel.ALL else levels
        ranker.weight = weight.toDouble()
        lvls.forEach { lvl ->
            rankers.computeIfAbsent(lvl) { mutableListOf() }.add(ranker)
            maxScore[lvl] = getMaxScore(lvl) + weight
        }
    }

    fun getMaxScore(level: RankerLevel): Double {
        return maxScore.getOrDefault(level, 0.0)
    }

    fun getRankers(level: RankerLevel): List<Ranker<T>> {
        return rankers.getOrDefault(level, listOf())
    }

    @DslMarker
    annotation class RankerDslMarker

    @RankerDslMarker
    fun ranker(name: String, block: (a: T, b: T) -> Double): Ranker<T> {
        return object : Ranker<T> {
            override val name = name
            override var weight = 0.0
            override fun getScore(a: T, b: T): Double {
                return block(a, b)
            }
            override fun toString(): String = name
        }
    }
}