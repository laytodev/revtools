package dev.revtools.updater

import dev.revtools.updater.asm.ClassEnv
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

        println()
    }


}