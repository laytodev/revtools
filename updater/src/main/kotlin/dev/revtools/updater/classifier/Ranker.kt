package dev.revtools.updater.classifier

interface Ranker<T> {

    val name: String

    var weight: Double

    fun getScore(a: T, b: T): Double

}