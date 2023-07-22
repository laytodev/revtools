package dev.revtools.updater.asm

import java.io.File

class ClassEnv {

    val groupA = ClassGroup(this, false)
    val groupB = ClassGroup(this, false)
    val sharedGroup = ClassGroup(this, true)

    fun init(jarA: File, jarB: File) {
        groupA.init(jarA)
        groupB.init(jarB)
        groupA.process()
        groupB.process()
    }

}