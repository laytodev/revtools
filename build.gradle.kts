import java.net.URL

plugins {
    kotlin("jvm")
}

tasks.wrapper {
    gradleVersion = "8.2"
}

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        mavenLocal()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }

    dependencies {
        implementation(kotlin("stdlib"))
        implementation(kotlin("reflect"))
    }
}


val TaskContainer.downloadGamepack by tasks.registering {
    group = "revtools"
    doLast {
        downloadGamepack()
    }
}

val TaskContainer.deobfuscateGamepack by tasks.registering {
    group = "revtools"
    dependsOn(project(":deobfuscator").tasks.getByName("build"))
    doLast {
        project(":deobfuscator").tasks.named<JavaExec>("run") {
            args = listOf("gamepack.jar", "gamepack.deob.jar", "-t")
        }.get().exec()
    }
}

fun downloadGamepack() {
    println("Downloading latest Old School RuneScape gamepack jar...")

    val file = file("gamepack.jar")
    if(file.exists()) {
        println("Overwriting existing gamepack.jar file.")
        file.deleteRecursively()
    }

    val url = URL("http://oldschool1.runescape.com/gamepack.jar")
    val bytes = url.openConnection().getInputStream().readAllBytes()

    file.createNewFile()
    file.outputStream().use { it.write(bytes) }

    println("Completed download of gamepack jar.")
}