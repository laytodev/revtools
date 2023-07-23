package dev.revtools.updater

import dev.revtools.updater.asm.ClassEnv
import dev.revtools.updater.asm.ClassInstance
import dev.revtools.updater.asm.FieldInstance
import dev.revtools.updater.asm.MethodInstance
import dev.revtools.updater.classifier.ClassifierUtil
import dev.revtools.updater.classifier.ClassifierUtil.filterPotentialMatches
import java.io.File

object Updater {

    var runTestClient = false
    val env = ClassEnv()

    @JvmStatic
    fun main(args: Array<String>) {
        if(args.size < 3) error("Usage: updater.jar <old/named jar> <new/deob jar> <output jar> [-t]")
        val jarA = File(args[0])
        val jarB = File(args[1])
        val jarOut = File(args[2])
        runTestClient = (args.size == 4 && args[3] == "-t")

        /*
         * Run the updater
         */
        run(jarA, jarB, jarOut)
    }

    fun run(jarA: File, jarB: File, jarOut: File) {
        /*
         * Initialization
         */
        println("Initializing...")
        env.init(jarA, jarB)

        println("Starting matching...")

        matchUnobfuscatedClasses()
        matchSharedClassMemberRefs()

        env.groupA.classes.forEach { clsA ->
            if(clsA.hasMatch() && clsA.name != clsA.match!!.name) println("Mismatch: $clsA -> ${clsA.match!!}")
            clsA.methods.forEach { methodA ->
                if(methodA.hasMatch() && methodA.name != methodA.match!!.name) println("Mismatch: $methodA -> ${methodA.match!!}")
            }
            clsA.fields.forEach { fieldA ->
                if(fieldA.hasMatch() && fieldA.name != fieldA.match!!.name) println("Mismatch: $fieldA -> ${fieldA.match!!}")
            }
        }

        printMatchStats()

        println()
    }

    private fun matchUnobfuscatedClasses() {
        env.groupA.classes.forEach { clsA ->
            if(clsA.isObfuscated) return@forEach
            val clsB = env.groupB.getClass(clsA.name)
            if(clsB != null && !clsB.isObfuscated) {
                match(clsA, clsB)
            }
        }
    }

    private fun matchSharedClassMemberRefs() {
        env.sharedGroup.classes.forEach { sharedCls ->
            val refsA = sharedCls.methodTypeRefs.filter { it.group == env.groupA }.toSet()
            val refsB = sharedCls.methodTypeRefs.filter { it.group == env.groupB }.toSet()
            refsA.forEach methodLoop@ { methodA ->
                if(methodA.hasMatch()) return@methodLoop
                val matches = refsB.filterPotentialMatches(methodA)
                if(matches.size == 1) {
                    val methodB = matches.first()
                    if(!methodB.hasMatch()) {
                        match(methodA, methodB)
                    }
                }
            }
        }

        env.sharedGroup.classes.forEach { sharedCls ->
            val refsA = sharedCls.fieldTypeRefs.filter { it.group == env.groupA }.toSet()
            val refsB = sharedCls.fieldTypeRefs.filter { it.group == env.groupB }.toSet()
            refsA.forEach fieldLoop@ { fieldA ->
                if(fieldA.hasMatch()) return@fieldLoop
                val matches = refsB.filter { ClassifierUtil.isPotentiallyEqual(it.typeClass, fieldA.typeClass) }.toSet()
                if(matches.size == 1) {
                    val fieldB = matches.first()
                    if(!fieldB.hasMatch()) {
                        match(fieldA, fieldB)
                    }
                }
            }
        }
    }

    fun match(a: ClassInstance, b: ClassInstance) {
        if(a.match == b) return

        if(a.hasMatch()) {
            a.match!!.unmatch()
            unmatchMembers(a)
        }

        if(b.hasMatch()) {
            b.match!!.unmatch()
            unmatchMembers(b)
        }

        a.match = b
        b.match = a

        a.methods.forEach { methodA ->
            if(!methodA.isObfuscated) {
                val methodB = b.getMethod(methodA.name, methodA.desc)
                if(methodB != null && !methodB.isObfuscated) {
                    match(methodA, methodB)
                    return@forEach
                }
            }
        }

        a.fields.forEach { fieldA ->
            if(!fieldA.isObfuscated) {
                val fieldB = b.getField(fieldA.name, fieldA.desc)
                if(fieldB != null && !fieldB.isObfuscated) {
                    match(fieldA, fieldB)
                    return@forEach
                }
            }
        }

        println("Matched class: $a -> $b.")
    }

    fun match(a: MethodInstance, b: MethodInstance) {
        if(a.match == b) return

        if(a.hasMatch()) {
            a.match!!.unmatch()
            a.unmatch()
        }

        if(b.hasMatch()) {
            b.match!!.unmatch()
            b.unmatch()
        }

        a.match = b
        b.match = a

        a.argTypes.forEachIndexed { i, argA ->
            val argB = b.argTypes[i]
            if(ClassifierUtil.isPotentiallyEqual(argA, argB)) {
                match(argA, argB)
            }
        }

        if(ClassifierUtil.isPotentiallyEqual(a.retType, b.retType)) {
            match(a.retType, b.retType)
        }

        a.parents.forEachIndexed { i, parentA ->
            if(b.parents.isEmpty()) return@forEachIndexed
            val parentB = b.parents.toList()[i]
            if(ClassifierUtil.isPotentiallyEqual(parentA, parentB)) {
                match(parentA, parentB)
            }
        }

        a.children.forEachIndexed { i, childA ->
            if(b.children.isEmpty()) return@forEachIndexed
            val childB = b.children.toList()[i]
            if(ClassifierUtil.isPotentiallyEqual(childA, childB)) {
                match(childA, childB)
            }
        }

        if(!a.isStatic() && !b.isStatic() && !a.cls.hasMatch()) {
            match(a.cls, b.cls)
        }

        println("Matched method: $a -> $b.")
    }

    private fun match(a: FieldInstance, b: FieldInstance) {
        if(a.match == b) return

        if(a.hasMatch()) {
            a.match!!.unmatch()
            a.unmatch()
        }

        if(b.hasMatch()) {
            b.match!!.unmatch()
            b.unmatch()
        }

        a.match = b
        b.match = a

        if(ClassifierUtil.isPotentiallyEqual(a.typeClass, b.typeClass)) {
            match(a.typeClass, b.typeClass)
        }

        a.parents.forEachIndexed { i, parentA ->
            if(b.parents.isEmpty()) return@forEachIndexed
            val parentB = b.parents.toList()[i]
            if(ClassifierUtil.isPotentiallyEqual(parentA, parentB)) {
                match(parentA, parentB)
            }
        }

        a.children.forEachIndexed { i, childA ->
            if(b.children.isEmpty()) return@forEachIndexed
            val childB = b.children.toList()[i]
            if(ClassifierUtil.isPotentiallyEqual(childA, childB)) {
                match(childA, childB)
            }
        }

        if(!a.isStatic() && !b.isStatic() && !a.cls.hasMatch()) {
            match(a.cls, b.cls)
        }

        println("Matched field: $a -> $b.")
    }

    private fun unmatchMembers(cls: ClassInstance) {
        cls.memberMethods.forEach { method ->
            if(method.hasMatch()) {
                method.match!!.unmatch()
                method.unmatch()
            }
        }

        cls.memberFields.forEach { field ->
            if(field.hasMatch()) {
                field.match!!.unmatch()
                field.unmatch()
            }
        }
    }

    private fun printMatchStats() {
        var totalClassCount = 0
        var matchedClassCount = 0
        var totalMethodCount = 0
        var matchedMethodCount = 0
        var totalFieldCount = 0
        var matchedFieldCount = 0

        env.groupA.classes.forEach { cls ->
            totalClassCount++
            if(cls.hasMatch()) matchedClassCount++
            cls.methods.forEach { method ->
                totalMethodCount++
                if(method.hasMatch()) matchedMethodCount++
            }
            cls.fields.forEach { field ->
                totalFieldCount++
                if(field.hasMatch()) matchedFieldCount++
            }
        }

        val classesMsg = String.format("Classes: %d / %d (%.2f%%)", matchedClassCount, totalClassCount, (if(totalClassCount == 0) 0 else 100.0 * matchedClassCount / totalClassCount))
        val methodsMsg = String.format("Methods: %d / %d (%.2f%%)", matchedMethodCount, totalMethodCount, (if(totalMethodCount == 0) 0 else 100.0 * matchedMethodCount / totalMethodCount))
        val fieldsMsg = String.format("Fields: %d / %d (%.2f%%)", matchedFieldCount, totalFieldCount, (if(totalFieldCount == 0) 0 else 100.0 * matchedFieldCount / totalFieldCount))

        println("===== MATCHING STATS =====")
        println(classesMsg)
        println(methodsMsg)
        println(fieldsMsg)
        println("==========================")
    }
}