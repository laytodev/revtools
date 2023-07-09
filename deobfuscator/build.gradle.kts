plugins {
    application
}

dependencies {
    implementation("org.tinylog:tinylog-api-kotlin:_")
    implementation("org.tinylog:tinylog-impl:_")
    implementation("org.ow2.asm:asm:_")
    implementation("org.ow2.asm:asm-commons:_")
    implementation("org.ow2.asm:asm-util:_")
    implementation("org.ow2.asm:asm-tree:_")
    implementation("com.google.guava:guava:_")
    implementation("it.unimi.dsi:fastutil:_")
    implementation("org.jgrapht:jgrapht-core:_")
    runtimeOnly("org.bouncycastle:bcprov-jdk15on:1.52")
    runtimeOnly("org.json:json:20220320")
}

application {
    mainClass.set("dev.revtools.deobfuscator.Deobfuscator")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}