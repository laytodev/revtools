package dev.revtools.deobfuscator.transformer

import dev.revtools.deobfuscator.Transformer
import dev.revtools.deobfuscator.asm.ClassHierarchy
import dev.revtools.deobfuscator.asm.remap.NameMap
import dev.revtools.deobfuscator.asm.tree.*
import org.tinylog.kotlin.Logger

class NameGenerator : Transformer {

    private var classCount = 0
    private var methodCount = 0
    private var fieldCount = 0

    private val nameMap = NameMap()

    override fun run(group: ClassGroup) {
        generateMappings(group)
        applyMappings(group)
    }

    private fun generateMappings(group: ClassGroup) {
        Logger.info("Generating name mappings.")

        val hierarchy = ClassHierarchy(group)

        // Generate Class Names
        group.classes.forEach classLoop@ { cls ->
            if(!cls.name.isObfuscatedName()) return@classLoop
            val newName = "class${++classCount}"
            nameMap.map(cls.id, newName)
        }

        // Generate Method Names
        group.classes.forEach { cls ->
            methodLoop@ for(method in cls.methods) {
               if(!method.name.isObfuscatedName() || nameMap.isMapped(method.id)) continue@methodLoop
                val newName = "method${++methodCount}"
                val memberId = "${method.name}${method.desc}"

                // Add mapping for current owner.
                nameMap.map("${method.owner.id}.$memberId", newName)

                // Loop over the child classes of the current owner and map possible overrides.
                hierarchy[method.owner.name]!!.childClasses.forEach { child ->
                    nameMap.map("$child.$memberId", newName)
                }
            }
        }

        // Generate Field Names
        group.classes.forEach { cls ->
            fieldLoop@ for(field in cls.fields) {
                if(!field.name.isObfuscatedName() || nameMap.isMapped(field.id)) continue@fieldLoop
                val newName = "field${++fieldCount}"
                val memberId = field.name

                // Add mapping for current owner.
                nameMap.map("${field.owner.id}.$memberId", newName)

                // Add mappings for all child classes from current owner.
                hierarchy[field.owner.name]!!.childClasses.forEach { child ->
                    nameMap.map("$child.$memberId", newName)
                }
            }
        }
    }

    private fun applyMappings(group: ClassGroup) {
        Logger.info("Applying name mappings.")

        group.remap(nameMap)

        /*
         * Finished applying names
         */
        Logger.info("Renamed $classCount classes.")
        Logger.info("Renamed $methodCount methods.")
        Logger.info("Renamed $fieldCount fields.")
    }

    private fun String.isObfuscatedName(): Boolean {
        val ignores = arrayOf("add", "get", "set", "put", "add", "run")
        return (this.length <= 2) || (this.length == 3 && this !in ignores)
    }
}