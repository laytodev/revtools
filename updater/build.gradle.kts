plugins {
    application
}

application {
    mainClass.set("dev.revtools.updater.Updater")
    executableDir = rootProject.projectDir.path
}

dependencies {
    implementation("org.ow2.asm:asm:_")
    implementation("org.ow2.asm:asm-util:_")
    implementation("org.ow2.asm:asm-commons:_")
    implementation("org.ow2.asm:asm-tree:_")
    implementation("com.google.guava:guava:_")
    implementation("org.bouncycastle:bcprov-jdk15on:1.52")
    implementation("org.json:json:20220320")
    implementation("me.tongfei:progressbar:_")
    implementation("dev.reimer:progressbar-ktx:_")
}