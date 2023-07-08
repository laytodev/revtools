package dev.revtools.deobfuscator.asm.remap

import dev.revtools.deobfuscator.asm.tree.id
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

class NameMap {

    val mappings = hashMapOf<String, String>()

    fun map(id: String, newName: String) {
        mappings[id] = newName
    }

    fun get(id: String): String? = mappings[id]

    fun isMapped(id: String) = mappings[id] != null
}