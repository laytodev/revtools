package dev.revtools.deobfuscator.transformer

import com.google.common.collect.Multimap
import com.google.common.collect.MultimapBuilder
import dev.revtools.deobfuscator.Deobfuscator.Companion.isDeobfuscatedName
import dev.revtools.deobfuscator.Transformer
import dev.revtools.deobfuscator.asm.tree.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.tinylog.kotlin.Logger

class UnusedMethodRemover : Transformer {

    private var count = 0

    override fun run(group: ClassGroup) {
        val superClasses = MultimapBuilder.hashKeys().arrayListValues().build<ClassNode, String>()
        val subClasses = MultimapBuilder.hashKeys().arrayListValues().build<ClassNode, String>()

        group.classes.forEach { cls ->
            cls.interfaces.forEach { superClasses.put(cls, it) }
            superClasses.put(cls, cls.superName)
        }

        superCl
        sses.forEach { cls, superName ->
            if(group.getClass(superName) != null) {
                subClasses.put(group.getClass(superName), cls.name)
            }
        }

        val usedMethods = group.classes.flatMap { it.methods.asSequence() }
            .flatMap { it.instructions.iterator().asSequence() }
            .mapNotNull { it as? MethodInsnNode }
            .map { "${it.owner}.${it.name}${it.desc}" }
            .toSet()

        val toRemove = hashSetOf<String>()
        group.classes.forEach { cls ->
            for(method in cls.methods) {
                if(method.isUsed(usedMethods, superClasses, subClasses)) continue
                toRemove.add(method.id)
            }
        }

        group.classes.forEach { cls ->
            val methods = cls.methods.iterator()
            while(methods.hasNext()) {
                val method = methods.next()
                if(method.id !in toRemove) continue
                methods.remove()
                count++
            }
        }

        Logger.info("Removed $count unused methods")
    }

    private fun MethodNode.isUsed(usedMethods: Set<String>, superClasses: Multimap<ClassNode, String>, subClasses: Multimap<ClassNode, String>): Boolean {
        if(isConstructor() || isInitializer()) return true
        if(!name.isDeobfuscatedName()) return true
        if(usedMethods.contains(id)) return true

        var supers = superClasses[owner]
        while(supers.isNotEmpty()) {
            supers.forEach { cls ->
                if(isJvmMethod(cls, name, desc)) return true
                if(usedMethods.contains("$cls.$name$desc")) return true
            }
            supers = supers.filter { group.getClass(it) != null }.flatMap { superClasses[group.getClass(it)] }
        }

        var subs = subClasses[owner]
        while(subs.isNotEmpty()) {
            subs.forEach { cls ->
                if(usedMethods.contains("$cls.$name$desc")) return true
            }
            subs = subs.flatMap { subClasses[group.getClass(it)] }
        }

        return false
    }

    private fun isJvmMethod(owner: String, name: String, desc: String): Boolean {
        try {
            var classes = listOf(Class.forName(Type.getObjectType(owner).className))
            while(classes.isNotEmpty()) {
                for(cls in classes) {
                    if(cls.declaredMethods.any { it.name == name && Type.getMethodDescriptor(it) == desc}) {
                        return true
                    }
                }
                classes = classes.flatMap {
                    mutableListOf<Class<*>>().apply {
                        addAll(it.interfaces)
                        if(it.superclass != null) {
                            add(it.superclass)
                        }
                    }
                }
            }
        } catch(e: Exception) { /* Do nothing */ }
        return false
    }
}