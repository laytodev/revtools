package dev.revtools.deobfuscator.asm.tree

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
        repeat(2) { step ->
            classes.forEach { cls ->
                when(step) {
                    0 -> cls.buildA()
                    1 -> cls.buildB()
                    else -> throw IllegalStateException("Unhandled build step $step.")
                }
            }
        }
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

    private fun ClassNode.buildA() {
        if(superName != null) {
            parent = getClass(superName)
            parent?.children?.add(this)
        }

        interfaces.mapNotNull { getClass(it) }.forEach { itf ->
            interfaceClasses.add(itf)
            itf.implementers.add(this)
        }
    }

    private fun ClassNode.buildB() {
        methods.forEach { it.build() }
        fields.forEach { it.build() }
    }

    private fun MethodNode.build() {
        val insns = instructions.iterator()
        while(insns.hasNext()) {
            val insn = insns.next()

            when(insn.type) {
                METHOD_INSN -> {
                    insn as MethodInsnNode
                    val owner = group.getClass(insn.owner) ?: continue
                    val dst = owner.resolveMethod(insn.name, insn.desc) ?: continue

                    dst.refsIn.add(this)
                    refsOut.add(dst)
                    dst.owner.methodTypeRefs.add(this)
                    classRefs.add(dst.owner)
                }

                FIELD_INSN -> {
                    insn as FieldInsnNode
                    val owner = group.getClass(insn.owner) ?: continue
                    val dst = owner.resolveField(insn.name, insn.desc) ?: continue

                    if(insn.opcode == GETSTATIC || insn.opcode == GETFIELD) {
                        dst.readRefs.add(this)
                        fieldReadRefs.add(dst)
                    } else {
                        dst.writeRefs.add(this)
                        fieldWriteRefs.add(dst)
                    }

                    dst.owner.methodTypeRefs.add(this)
                    classRefs.add(dst.owner)
                }

                TYPE_INSN -> {
                    insn as TypeInsnNode
                    val dst = group.getClass(insn.desc) ?: continue
                    dst.methodTypeRefs.add(this)
                    classRefs.add(dst)
                }
            }

            if(insn is LineNumberNode) {
                lineNumbers.add(insn.line)
            }
        }
    }

    private fun FieldNode.build() {

    }
}