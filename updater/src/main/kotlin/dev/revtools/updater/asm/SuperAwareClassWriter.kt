package dev.revtools.updater.asm

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode

class SuperAwareClassWriter(classes: List<ClassNode>) : ClassWriter(COMPUTE_FRAMES or COMPUTE_MAXS) {

    companion object {
        val OBJECT_INTERNAL_NAME = Type.getInternalName(Any::class.java)
    }

    private val classNames = classes.associate { it.name to it }

    override fun getCommonSuperClass(type1: String, type2: String): String {
        if(isAssignable(type1, type2)) return type2
        if(isAssignable(type2, type1)) return type1
        var t1 = type1
        do {
            t1 = checkNotNull(superClassName(t1, classNames))
        } while(!isAssignable(type2, t1))
        return t1
    }

    private fun isAssignable(from: String, to: String): Boolean {
        if(from == to) return true
        val superClass = superClassName(from, classNames) ?: return true
        if(isAssignable(superClass, to)) return true
        return interfaceNames(from).any { isAssignable(it, to) }
    }

    private fun interfaceNames(type: String): List<String> {
        return if(type in classNames) {
            classNames.getValue(type).interfaces
        } else {
            Class.forName(type.replace('/', '.')).interfaces.map { Type.getInternalName(it) }
        }
    }

    private fun superClassName(type: String, classNames: Map<String, ClassNode>): String? {
        return if(type in classNames) {
            classNames.getValue(type).superName
        } else {
            val c = Class.forName(type.replace('/', '.'))
            if(c.isInterface) {
                OBJECT_INTERNAL_NAME
            } else {
                c.superclass?.let { Type.getInternalName(it) }
            }
        }
    }
}