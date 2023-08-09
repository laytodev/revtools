package dev.revtools.deobfuscator.transformer

import dev.revtools.deobfuscator.Transformer
import dev.revtools.deobfuscator.asm.tree.ClassGroup
import dev.revtools.deobfuscator.asm.tree.isConstructor
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode
import org.tinylog.kotlin.Logger

class EmptyClassRemover : Transformer {

    private var count = 0
    private val emptyClasses = mutableSetOf<String>()
    private val referencedClasses = mutableSetOf<String>()

    override fun run(group: ClassGroup) {
        var changed: Boolean
        do {
            changed = prePass(group)
            for(cls in group.classes) {
                changed = changed or transformClass(cls)
                cls.methods.forEach { method ->
                    changed = changed or preTransformMethod(method)
                    changed = changed or transformMethod(method)
                }
                cls.fields.forEach { field ->
                    changed = changed or transformField(field)
                }
            }
            changed = changed or postPass(group)
        } while(changed)

        Logger.info("Removed $count empty classes.")
    }

    private fun prePass(group: ClassGroup): Boolean {
        referencedClasses.clear()
        return false
    }

    private fun transformClass(cls: ClassNode): Boolean {
        if((cls.methods.isEmpty() || (cls.methods.size == 1 && cls.methods.first().isConstructor())) && cls.fields.isEmpty()) {
            emptyClasses.add(cls.name)
        }

        if(cls.superName != null) {
            referencedClasses.add(cls.superName)
        }

        cls.interfaces.forEach { itf ->
            referencedClasses.add(itf)
        }

        if(cls.visibleAnnotations != null) {
            cls.visibleAnnotations.forEach {
                referencedClasses.add(Type.getType(it.desc).internalName)
            }
        }

        if(cls.invisibleAnnotations != null) {
            cls.invisibleAnnotations.forEach {
                referencedClasses.add(Type.getType(it.desc).internalName)
            }
        }

        return false
    }

    private fun preTransformMethod(method: MethodNode): Boolean {
        addTypeReference(Type.getType(method.desc))
        return false
    }

    private fun transformMethod(method: MethodNode): Boolean {
        for(insn in method.instructions) {
            when(insn) {
                is LdcInsnNode -> {
                    val cst = insn.cst
                    if(cst is Type) {
                        addTypeReference(cst)
                    }
                }
                is TypeInsnNode -> referencedClasses.add(insn.desc)
            }
        }

        return false
    }

    private fun transformField(field: FieldNode): Boolean {
        addTypeReference(Type.getType(field.desc))
        return false
    }

    private fun postPass(group: ClassGroup): Boolean {
        var changed = false
        for(name in emptyClasses.subtract(referencedClasses)) {
            if(group.remove(name) != null) {
                count++
                changed = true
            }
        }
        return changed
    }

    private fun addTypeReference(type: Type) {
        when(type.sort) {
            Type.OBJECT -> referencedClasses.add(type.internalName)
            Type.ARRAY -> addTypeReference(type.elementType)
            Type.METHOD -> {
                type.argumentTypes.forEach(::addTypeReference)
                addTypeReference(type.returnType)
            }
        }
    }
}