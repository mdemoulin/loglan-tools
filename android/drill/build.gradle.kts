import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget = JvmTarget.JVM_17
}

dependencies {
    implementation(libs.serialization.json)
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

// The lists, built from the sources in this repository by loglan-update,
// as the app ships them; and what loglan-teach itself makes of them
// (golden/make-golden), which the tests hold the Kotlin drill to.
val repo = rootDir.parentFile
val listsDir = layout.buildDirectory.dir("lists")
val goldenFile = layout.buildDirectory.file("golden/golden.json")

val buildLists = tasks.register<Exec>("buildLists") {
    description = "Builds the Loglan lists with loglan-update, offline."
    inputs.dir(repo.resolve("sources"))
    inputs.dir(repo.resolve("lists"))
    inputs.file(repo.resolve("loglan-teach/loglan-update"))
    inputs.file(repo.resolve("loglan-teach/mk-utility"))
    outputs.dir(listsDir)
    val dir = listsDir.get().asFile
    doFirst { dir.deleteRecursively(); dir.mkdirs() }
    commandLine(repo.resolve("loglan-teach/loglan-update").path,
                "--dir", dir.path, "--dist", repo.path, "--offline")
}

val makeGolden = tasks.register<Exec>("makeGolden") {
    description = "Records what loglan-teach makes of the lists, for the tests."
    dependsOn(buildLists)
    inputs.dir(listsDir)
    inputs.file(repo.resolve("loglan-teach/loglan-teach"))
    inputs.file(file("golden/make-golden"))
    outputs.file(goldenFile)
    val out = goldenFile.get().asFile
    doFirst { out.parentFile.mkdirs() }
    commandLine(file("golden/make-golden").path,
                repo.resolve("loglan-teach/loglan-teach").path,
                listsDir.get().asFile.path, out.path)
}

tasks.test {
    useJUnitPlatform()
    dependsOn(makeGolden)
    systemProperty("golden", goldenFile.get().asFile.path)
    systemProperty("lists", listsDir.get().asFile.path)
}
