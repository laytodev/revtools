package dev.revtools.deobfuscator

import dev.revtools.deobfuscator.asm.tree.ClassGroup
import dev.revtools.deobfuscator.transformer.TestTransformer
import org.tinylog.kotlin.Logger
import java.io.File
import kotlin.reflect.full.createInstance

class Deobfuscator(private val inputFile: File, private val outputFile: File) {

    private var runTestClient = false

    private val group = ClassGroup()
    private val transformers = mutableListOf<Transformer>()

    private fun init() {
        Logger.info("Initializing deobfuscator.")
        group.clear()
        transformers.clear()

        Logger.info("Loading input jar: ${inputFile.name}.")
        group.readJar(inputFile)
        group.removeIf { it.name.startsWith("org/") }
        group.build()
        Logger.info("Loaded ${group.classes.toList().size} input classes.")

        Logger.info("Registering transformers.")

        register<TestTransformer>()

        Logger.info("Registerd ${transformers.size} transformers.")
    }

    fun run() {
        init()

        Logger.info("Starting deobfuscation.")
        transformers.forEach { transformer ->
            Logger.info("Running transformer: ${transformer::class.simpleName}.")
            transformer.run(group)
        }
        Logger.info("Finished deobfuscation")

        Logger.info("Writing classes to output jar: ${outputFile.name}.")
        group.writeJar(outputFile)
        Logger.info("Successfully saved ${group.classes.toList().size} classes to output jar file.")

        if(runTestClient) {
            Logger.info("Starting test client.")
            TestClient(outputFile).start()
        }

        Logger.info("Deobfuscator finished successfully.")
    }

    private inline fun <reified T : Transformer> register() {
        transformers.add(T::class.createInstance())
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            if(args.size < 2) error("Usage: deobfuscator.jar <input-jar> <output-jar> [-t]")
            val inputFile = File(args[0])
            val outputFile = File(args[1])

            val deobfuscator = Deobfuscator(inputFile, outputFile)
            if(args.size == 3 && args[2] == "-t") {
                deobfuscator.runTestClient = true
            }

            deobfuscator.run()
        }
    }
}