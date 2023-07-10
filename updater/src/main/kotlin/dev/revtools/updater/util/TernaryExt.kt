package dev.revtools.updater.util

class Ternary<T>(val expr: Boolean, val a: T)
infix fun <T> Boolean.then(a: T): Ternary<T> = Ternary(this, a)
infix fun <T> Ternary<T>.or(b: T) = if(expr) a else b

inline infix fun (() -> Unit).then(crossinline predicate: () -> Boolean): Boolean {
    this()
    return predicate()
}