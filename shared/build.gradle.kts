plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kmp.library)
}

// rules/*.json is the single source of truth (ADR-0003). Embed the bundled packs, and the
// Android fixtures for tests, as Kotlin string constants so no platform needs its own
// resource-loading code.
val generateBundledRules by tasks.registering(EmbedJsonTask::class) {
    sourceDir.set(rootProject.layout.projectDirectory.dir("rules"))
    include.set("*.json")
    objectName.set("BundledRules")
    outputDir.set(layout.buildDirectory.dir("generated/bundledRules/kotlin"))
}

val generateFixtures by tasks.registering(EmbedJsonTask::class) {
    sourceDir.set(rootProject.layout.projectDirectory.dir("rules/fixtures/android"))
    include.set("*.json")
    objectName.set("AndroidFixtures")
    outputDir.set(layout.buildDirectory.dir("generated/fixtures/kotlin"))
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "me.lgcode.ianua.shared"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
    }

    // Node only: the extension's webpack build consumes this as a plain JS library, and
    // commonTest runs under Node (the browser runner would pull in Karma).
    js {
        nodejs()
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateBundledRules)
            dependencies {
                api(libs.kotlinx.serialization.json)
            }
        }
        commonTest {
            kotlin.srcDir(generateFixtures)
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

abstract class EmbedJsonTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDir: DirectoryProperty

    @get:Input
    abstract val include: Property<String>

    @get:Input
    abstract val objectName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val files = sourceDir.get().asFileTree.matching { include(this@EmbedJsonTask.include.get()) }
            .files.filter { it.parentFile == sourceDir.get().asFile }.sortedBy { it.name }
        val entries = files.joinToString("\n") { file ->
            val text = file.readText().replace("\$", "\${'\$'}")
            "        \"${file.nameWithoutExtension}\" to \"\"\"$text\"\"\","
        }
        val out = outputDir.get().file("me/lgcode/ianua/generated/${objectName.get()}.kt").asFile
        out.parentFile.mkdirs()
        out.writeText(
            """
            |// Generated from ${sourceDir.get().asFile.name}/ by :shared:${name}. Do not edit.
            |package me.lgcode.ianua.generated
            |
            |object ${objectName.get()} {
            |    val all: Map<String, String> = mapOf(
            |$entries
            |    )
            |}
            |""".trimMargin(),
        )
    }
}

// Android lint reads the source directories directly, without the task dependency that
// kotlin.srcDir(task) carries for compilation.
tasks.matching { it.name.startsWith("lint") || it.name.endsWith("LintModel") }.configureEach {
    dependsOn(generateBundledRules, generateFixtures)
}
