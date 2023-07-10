package dev.revtools.updater.asm

abstract class MemberEntry<T : MemberEntry<T>>(val cls: ClassEntry) : Matchable<T>() {
    val group get() = cls.group
    val env get() = cls.env
    var parent: T? = null
    val children = hashSetOf<T>()
}