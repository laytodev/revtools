package dev.revtools.updater.util

fun String.isObfuscatedName(): Boolean {
    return (this.length <= 2 || (this.length == 3 && this !in arrayOf("run", "add", "get", "set", "put"))) ||
            arrayOf("class", "method", "field").any { this.startsWith(it) }
}