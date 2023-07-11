package dev.revtools.updater.remap

import java.util.SortedMap
import java.util.TreeMap

data class NameMap(
    val classes: SortedMap<String, String>,
    val methods: SortedMap<MemberRef, Method>,
    val fields: SortedMap<MemberRef, Field>
) {
    data class Method(val owner: String, val name: String)
    data class Field(val owner: String, val name: String)

    constructor() : this(TreeMap(), TreeMap(), TreeMap())

    fun add(other: NameMap) {
        classes.putAll(other.classes)
        methods.putAll(other.methods)
        fields.putAll(other.fields)
    }
}