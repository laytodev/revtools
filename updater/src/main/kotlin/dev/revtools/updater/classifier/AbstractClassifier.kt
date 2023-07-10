package dev.revtools.updater.classifier

import dev.revtools.updater.asm.Matchable

abstract class AbstractClassifier<T : Matchable<T>> {

    val rankers = mutableListOf<Ranker<T>>()
    var maxScore = 0.0

    abstract fun init()

    fun addRanker(ranker: Ranker<T>, weight: Int) {
        ranker.weight = weight.toDouble()
        rankers.add(ranker)
        maxScore += weight.toDouble()
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
        }
    }
}