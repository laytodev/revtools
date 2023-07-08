package dev.revtools.deobfuscator.asm.util

import dev.revtools.deobfuscator.asm.remap.ExtendedRemapper
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.FrameNode
import org.objectweb.asm.tree.InnerClassNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.MultiANewArrayInsnNode
import org.objectweb.asm.tree.ParameterNode
import org.objectweb.asm.tree.TryCatchBlockNode
import org.objectweb.asm.tree.TypeInsnNode

fun ClassNode.remap(remapper: ExtendedRemapper) {
    val origName = name
    name = remapper.mapType(origName)
    signature = remapper.mapSignature(signature, false)
    superName = remapper.mapType(superName)
    interfaces = interfaces?.map(remapper::mapType)

    val origOuterClass = outerClass
    outerClass = remapper.mapType(origOuterClass)

    if(outerMethod != null) {
        outerMethod = remapper.mapMethodName(origOuterClass, outerMethod, outerMethodDesc)
        outerMethodDesc = remapper.mapMethodDesc(outerMethodDesc)
    }

    innerClasses.forEach { innerClass ->
        innerClass.remap(remapper)
    }

    fields.forEach { field ->
        field.remap(remapper, origName)
    }

    methods.forEach { method ->
        method.remap(remapper, origName)
    }
}

fun InnerClassNode.remap(remapper: ExtendedRemapper) {
    name = remapper.mapType(name)
    outerName = remapper.mapType(outerName)
    innerName = remapper.mapType(innerName)
}

fun FieldNode.remap(remapper: ExtendedRemapper, owner: String) {
    name = remapper.mapFieldName(owner, name, desc)
    desc = remapper.mapDesc(desc)
    signature = remapper.mapSignature(signature, true)
    value = remapper.mapValue(value)
}

fun MethodNode.remap(remapper: ExtendedRemapper, owner: String) {
    if(parameters == null) {
        parameters = List(Type.getArgumentTypes(desc).size) { ParameterNode(null, 0) }
    }

    parameters.forEachIndexed { i, param ->
        param.remap(remapper, owner, name, desc, i)
    }

    name = remapper.mapMethodName(owner, name, desc)
    desc = remapper.mapMethodDesc(desc)
    signature = remapper.mapSignature(signature, false)
    exceptions = exceptions.map(remapper::mapType)

    /*
     * If the method body has potential executable code at runtime.
     */
    for(insn in instructions) {
        insn.remap(remapper)
    }

    for(tcb in tryCatchBlocks) {
        tcb.remap(remapper)
    }
}

fun ParameterNode.remap(remapper: ExtendedRemapper, owner: String, methodName: String, desc: String, index: Int) {
    name = remapper.mapParameterName(owner, methodName, desc, index, name)
}

fun TryCatchBlockNode.remap(remapper: ExtendedRemapper) {
    type = remapper.mapType(type)
}

fun AbstractInsnNode.remap(remapper: ExtendedRemapper) {
    when(this) {
        is FrameNode -> throw UnsupportedOperationException("FrameNode remapping not supported.")
        is InvokeDynamicInsnNode -> throw UnsupportedOperationException("InvokeDynamicNode remapping not supported.")
        is FieldInsnNode -> {
            val origOwner = owner
            owner = remapper.mapFieldOwner(origOwner, name, desc)
            name = remapper.mapFieldName(origOwner, name, desc)
            desc = remapper.mapDesc(desc)
        }
        is MethodInsnNode -> {
            val origOwner = owner
            owner = remapper.mapMethodOwner(origOwner, name, desc)
            name = remapper.mapMethodName(origOwner, name, desc)
            desc = remapper.mapMethodDesc(desc)
        }
        is TypeInsnNode -> desc = remapper.mapType(desc)
        is LdcInsnNode -> cst = remapper.mapValue(cst)
        is MultiANewArrayInsnNode -> desc = remapper.mapType(desc)
    }
}