package dev.revtools.updater.asm

import lukfor.progress.Components
import lukfor.progress.TaskService
import lukfor.progress.tasks.ITaskRunnable
import java.io.File

class ClassEnv {

    val groupA = ClassGroup(this, false)
    val groupB = ClassGroup(this, false)
    val sharedGroup = ClassGroup(this, true)

    fun addSharedClass(cls: ClassEntry): Boolean {
        if(sharedGroup.getClass(cls.name) != null) return false
        sharedGroup.addClass(cls)
        return true
    }

    fun getSharedClass(name: String) = sharedGroup.getClass(name)
    fun getSharedClassById(id: String) = sharedGroup.getClassById(id)

    fun init(jarA: File, jarB: File) {
        println("Initializing class environment.")

        println("Loading jar files.")
        groupA.init(jarA)
        groupB.init(jarB)

        println("Building class groups")
        groupA.process()
        groupB.process()

        println("Class environment completed initialization.")
    }

}