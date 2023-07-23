package dev.revtools.updater.asm

abstract class Matchable<T : Matchable<T>> {

    var isObfuscated: Boolean = true

    var match: T? = null

    fun hasMatch() = match != null

    fun unmatch() {
        match = null
    }
}