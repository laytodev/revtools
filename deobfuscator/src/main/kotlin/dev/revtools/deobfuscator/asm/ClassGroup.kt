package dev.revtools.deobfuscator.asm

import org.objectweb.asm.tree.ClassNode
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

    fun clear() {
        classMap.clear()
    }

    fun init() {

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
        file.parentFile.takeIf { it.isDirectory }?.mkdirs()
        JarOutputStream(file.outputStream()).use { jos ->
            classes.forEach { cls ->
                jos.putNextEntry(JarEntry("${cls.name}.class"))
                jos.write(cls.toBytes())
                jos.closeEntry()
            }
        }
    }
}