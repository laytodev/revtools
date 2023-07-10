package dev.revtools.updater

import dev.revtools.updater.asm.ClassEnv
import java.io.File

object Updater {

    val env = ClassEnv()

    fun init(jarA: File, jarB: File) {
        env.init(jarA, jarB)
    }

    fun run() {
        println("Running updater.")

        println()
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