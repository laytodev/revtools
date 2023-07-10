package dev.revtools.updater.asm

abstract class Matchable<T> {

    var isMatchable: Boolean = true

    var match: T? = null

    fun hasMatch() = match != null

}