package dev.revtools.deobfuscator.asm.remap

import dev.revtools.deobfuscator.asm.MemberRef
import dev.revtools.deobfuscator.asm.util.nextReal
import dev.revtools.deobfuscator.asm.tree.ClassGroup
import dev.revtools.deobfuscator.asm.tree.isInitializer
import dev.revtools.deobfuscator.asm.util.remap
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodNode
import java.util.SortedMap
import java.util.TreeMap
import kotlin.math.max

class ClassGroupRemapper(
    private val group: ClassGroup,
    private val nameMap: NameMap
) {

    private class Initializer(val instructions: InsnList, val maxStack: Int) {
        val dependencies = instructions.asSequence()
            .filterIsInstance<FieldInsnNode>()
            .filter { it.opcode == GETSTATIC }
            .map(::MemberRef)
            .toSet()
    }

    private class Method(val owner: String, val node: MethodNode, val version: Int)
    private class Field(val owner: String, val node: FieldNode, val version: Int, val initializer: Initializer?)

    private val remapper = NodeMapper(group, nameMap)

    private var classMap = group.classes.associateBy { it.name }.toSortedMap()

    private val methods = mutableListOf<Method>()
    private val fields = mutableMapOf<MemberRef, Field>()
    private val splicedFields = mutableSetOf<MemberRef>()

    fun remap(): SortedMap<String, ClassNode> {
        extractFields()
        extractMethods()

        classMap.values.forEach { cls ->
            cls.remap(remapper)
        }

        classMap = classMap.mapKeysTo(TreeMap()) { (_, c) -> c.name }.toSortedMap()

        spliceFields()
        spliceMethods()

        removeEmptyInitializerMethods()

        return classMap
    }

    private fun extractMethods() {
        for(cls in classMap.values) {
            cls.methods.removeIf { method ->
                val oldOwner = remapper.mapType(cls.name)
                val newOwner = remapper.mapMethodOwner(cls.name, method.name, method.desc)
                if(oldOwner == newOwner) return@removeIf false

                method.remap(remapper, cls.name)
                methods += Method(newOwner, method, cls.version)

                return@removeIf true
            }
        }
    }

    private fun extractFields() {
        for(cls in classMap.values) {
            cls.fields.removeIf { field ->
                val oldOwner = remapper.mapType(cls.name)
                val newOwner = remapper.mapFieldOwner(cls.name, field.name, field.desc)
                if(oldOwner == newOwner) return@removeIf false

                val initializer = extractInitializer(cls, field)
                field.remap(remapper, cls.name)

                val newMember = MemberRef(newOwner, field.name, field.desc)
                fields[newMember] = Field(newOwner, field, cls.version, initializer)

                return@removeIf true
            }
        }
    }

    private fun extractInitializer(cls: ClassNode, field: FieldNode): Initializer? {
        val clinit = cls.methods.find { it.isInitializer() } ?: return null
        val initializer = remapper.getFieldInitializer(cls.name, field.name, field.desc) ?: return null

        val insns = InsnList()
        for(insn in initializer) {
            clinit.instructions.remove(insn)
            insns.add(insn)
            insn.remap(remapper)
        }

        return Initializer(insns, clinit.maxStack)
    }

    private fun spliceMethods() {
        for(method in methods) {
            val cls = classMap.computeIfAbsent(method.owner, ::createClass)
            cls.version = if(((cls.version shl 16) or (cls.version ushr 16) >= ((method.version shl 16) or (method.version ushr 16)))) cls.version else method.version
            cls.methods.add(method.node)
        }
    }

    private fun spliceFields() {
        for(member in fields.keys) {
            spliceField(member)
        }
    }

    private fun spliceField(member: MemberRef) {
        if(!splicedFields.add(member)) return

        val field = fields[member] ?: return
        val cls = classMap.computeIfAbsent(field.owner, ::createClass)

        if(field.initializer != null) {
            for(dependency in field.initializer.dependencies) {
                spliceField(dependency)
            }

            val clinit = cls.methods.find { it.isInitializer() } ?: createInitializerMethod(cls)
            check(hasSingleExit(clinit.instructions)) { "${cls.name} <clinit> does not have single exit flow." }

            clinit.maxStack = max(clinit.maxStack, field.initializer.maxStack)
            clinit.instructions.insertBefore(clinit.instructions.last, field.initializer.instructions)
        }

        cls.version = if(((cls.version shl 16) or (cls.version ushr 16) >= ((field.version shl 16) or (field.version ushr 16)))) cls.version else field.version
        cls.fields.add(field.node)
    }

    private fun hasSingleExit(instructions: InsnList): Boolean {
        val insn = instructions.singleOrNull { it.opcode == RETURN }
        return insn != null && insn.nextReal == null
    }

    private fun removeEmptyInitializerMethods() {
        for(cls in classMap.values) {
            val clinit = cls.methods.find { it.isInitializer() } ?: continue
            val firstInsn = clinit.instructions.firstOrNull { it.opcode != -1 }
            if(firstInsn != null && firstInsn.opcode == RETURN) {
                cls.methods.remove(clinit)
            }
        }
    }

    private fun createClass(name: String): ClassNode {
        val cls = ClassNode()
        cls.version = V1_8
        cls.access = ACC_PUBLIC or ACC_SUPER or ACC_FINAL
        cls.name = name
        cls.superName = "java/lang/Object"
        cls.interfaces = mutableListOf()
        cls.innerClasses = mutableListOf()
        cls.fields = mutableListOf()
        cls.methods = mutableListOf()
        return cls
    }

    private fun createInitializerMethod(cls: ClassNode): MethodNode {
        val method = MethodNode()
        method.access = ACC_STATIC
        method.name = "<clinit>"
        method.desc = "()V"
        method.exceptions = mutableListOf()
        method.parameters = mutableListOf()
        method.instructions = InsnList()
        method.instructions.add(InsnNode(RETURN))
        method.tryCatchBlocks = mutableListOf()
        cls.methods.add(method)
        return method
    }
}