package dev.revtools.deobfuscator.asm.tree

import dev.revtools.deobfuscator.asm.remap.ClassGroupRemapper
import dev.revtools.deobfuscator.asm.remap.NameMap
import org.objectweb.asm.Opcodes.GETFIELD
import org.objectweb.asm.Opcodes.GETSTATIC
import org.objectweb.asm.tree.AbstractInsnNode.*
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

class ClassGroup {

    private var classMap = hashMapOf<String, ClassNode>()

    val classes get() = classMap.values.asSequence()

    fun add(cls: ClassNode): ClassNode? {
        cls.init(this)
        return classMap.put(cls.name, cls)
    }

    fun remove(cls: ClassNode): ClassNode? {
        return classMap.remove(cls.name)
    }

    fun replace(old: ClassNode, new: ClassNode) {
        remove(old)
        add(new)
    }

    fun removeIf(check: (ClassNode) -> Boolean) {
        val toRemove = hashSetOf<ClassNode>()
        classes.forEach { cls ->
            if(check(cls)) {
                toRemove.add(cls)
            }
        }
        toRemove.forEach { remove(it) }
    }

    fun clear() {
        classMap.clear()
    }

    fun getClass(name: String): ClassNode? = classMap[name]

    fun build() {
        classes.forEach { it.reset() }
        classes.forEach { it.build() }
    }

    fun readJar(file: File) {
        JarFile(file).use { jar ->
            jar.entries().asSequence().forEach { entry ->
                if(entry.name.endsWith(".class")) {
                    val node = ClassNode()
                    val bytes = jar.getInputStream(entry).readAllBytes()
                    node.fromBytes(bytes)
                    add(node)
                }
            }
        }
    }

    fun writeJar(file: File) {
        if(file.exists()) {
            file.deleteRecursively()
        }
        JarOutputStream(file.outputStream()).use { jos ->
            classes.forEach { cls ->
                jos.putNextEntry(JarEntry("${cls.name}.class"))
                jos.write(cls.toBytes())
                jos.closeEntry()
            }
        }
    }

    fun remap(nameMap: NameMap) {
        val newClassMap = ClassGroupRemapper(this, nameMap).remap().toMap() as HashMap<String, ClassNode>
        clear()
        newClassMap.values.forEach { add(it) }
        build()
    }
}