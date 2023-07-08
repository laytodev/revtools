package dev.revtools.deobfuscator.asm.remap

import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.tree.AbstractInsnNode

abstract class ExtendedRemapper : Remapper() {

    open fun mapMethodOwner(owner: String, name: String, desc: String): String {
        return mapType(owner)
    }

    open fun mapFieldOwner(owner: String, name: String, desc: String): String {
        return mapType(owner)
    }

    open fun mapParameterName(owner: String, name: String, desc: String, index: Int, parameterName: String?): String? {
        return parameterName
    }

    open fun getFieldInitializer(owner: String, name: String, desc: String): List<AbstractInsnNode>? {
        return null
    }
}