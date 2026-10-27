import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.mdemoulin.loglan.teach"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.mdemoulin.loglanteach"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":drill"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.core.ktx)
    debugImplementation(libs.compose.ui.tooling)
}

/**
 * The lists the app ships, built from the sources in this repository by
 * loglan-update, offline, as install.sh builds them into ~/.loglan; with a
 * VERSION the app goes by to know when to put new ones in place.
 */
abstract class BuildLoglanLists @Inject constructor(
    private val exec: ExecOperations,
    private val fs: FileSystemOperations,
) : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Internal
    abstract val repo: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun build() {
        val work = temporaryDir.resolve("lists")
        work.deleteRecursively()
        work.mkdirs()
        val repo = repo.get().asFile
        exec.exec {
            commandLine(repo.resolve("loglan-teach/loglan-update").path,
                        "--dir", work.path, "--dist", repo.path, "--offline")
            standardOutput = ByteArrayOutputStream()
        }
        val out = outputDir.get().asFile.resolve("loglan")
        fs.delete { delete(outputDir) }
        fs.copy {
            from(work) { include(LISTS) }
            from(repo.resolve("sources/NOTICE"))
            into(out)
        }
        val sha = MessageDigest.getInstance("SHA-256")
        out.walkTopDown().filter { it.isFile }.sortedBy { it.path }.forEach {
            sha.update(it.relativeTo(out).path.toByteArray())
            sha.update(it.readBytes())
        }
        out.resolve("VERSION").writeText(sha.digest().joinToString("") { "%02x".format(it) } + "\n")
    }

    companion object {
        val LISTS = listOf("core", "prims", "affs", "affsets", "utts", "l1prims", "m1-text", "derivs",
                           "aux/animals", "aux/ethnic")
    }
}

val repoDir = rootProject.layout.projectDirectory.dir("..")
val buildLoglanLists = tasks.register<BuildLoglanLists>("buildLoglanLists") {
    repo.set(repoDir)
    sources.from(repoDir.dir("sources"), repoDir.dir("lists"),
                 repoDir.file("loglan-teach/loglan-update"), repoDir.file("loglan-teach/mk-utility"))
    outputDir.set(layout.buildDirectory.dir("generated/loglan-assets"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(buildLoglanLists, BuildLoglanLists::outputDir)
    }
}
