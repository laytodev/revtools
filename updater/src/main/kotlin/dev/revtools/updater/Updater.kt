package dev.revtools.updater

import dev.revtools.updater.asm.ClassEntry
import dev.revtools.updater.asm.ClassEnv
import java.io.File

object Updater {

    val env = ClassEnv()

    fun init(jarA: File, jarB: File) {
        env.init(jarA, jarB)
        matchUnobfuscated()
    }

    fun run() {
        println("Running updater.")

        println()
    }

    private fun matchUnobfuscated() {
        env.groupA.classes.forEach { clsA ->
            if(!clsA.name.isObfuscatedName()) {
                val clsB = env.groupB.getClass(clsA.name)
                if(clsB != null && !clsB.name.isObfuscatedName()) {
                    match(clsA, clsB)
                }
            }
        }
    }

    fun match(a: ClassEntry, b: ClassEntry) {
        if(a.match == b) return
        if(a.hasMatch()) {
            a.match!!.match = null
            unmatchMembers(a)
        }
        if(b.hasMatch()) {
            b.match!!.match = null
            unmatchMembers(b)
        }
        a.match = b
        b.match = a

        if(a.isArray()) {
            if(!a.elementClass!!.hasMatch()) match(a.elementClass!!, b.elementClass!!)
        } else {
            a.arrayClasses.forEach { arrayA ->
                for(arrayB in b.arrayClasses) {
                    if(arrayB.hasMatch() || arrayB.dims != arrayA.dims) continue
                    match(arrayA, arrayB)
                    break
                }
            }
        }

        a.memberMethods.forEach { methodA ->
            if(!methodA.name.isObfuscatedName()) {
                val methodB = b.getMethod(methodA.name, methodA.desc)
            }
        }

        a.memberFields.forEach { fieldA ->
            if(!fieldA.name.isObfuscatedName()) {
                val fieldB = b.getField(fieldA.name, fieldA.desc)
            }
        }

        println("Matched Class: $a -> $b.")
    }

    fun unmatchMembers(cls: ClassEntry) {
        cls.memberMethods.forEach { method ->
            if(method.hasMatch()) {
                method.match!!.match = null
                method.match = null
            }
        }

        cls.memberFields.forEach { field ->
            if(field.hasMatch()) {
                field.match!!.match = null
                field.match = null
            }
        }
    }

    fun String.isObfuscatedName(): Boolean {
        return arrayOf("class", "method", "field").any { this.startsWith(it) }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        if(args.size != 3) error("Usage: updater.jar <old-jar> <new-jar> <output-jar>")
        val jarA = File(args[0])
        val jarB = File(args[1])
        val outputJar = File(args[2])

        init(jarA, jarB)
        run()
    }
}