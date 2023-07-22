package dev.revtools.updater.util

import java.util.Collections
import java.util.IdentityHashMap

fun <T> identityHashSetOf() = Collections.newSetFromMap<T>(IdentityHashMap())

fun <T> identityHashSetOf(vararg entries: T) = identityHashSetOf<T>().also { it.addAll(entries) }