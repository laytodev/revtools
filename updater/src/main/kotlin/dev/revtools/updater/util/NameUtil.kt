package dev.revtools.updater.util

fun String.isObfuscatedName(): Boolean {
    return arrayOf("class", "method", "field").any { this.startsWith(it) }
}