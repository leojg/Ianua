plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    // One bundle for every extension context (service worker, content script, gate page,
    // popup); Main.kt dispatches on where it is running. See docs/adr/0002.
    js {
        browser {
            commonWebpackConfig {
                outputFileName = "ianua.js"
            }
            testTask {
                enabled = false
            }
        }
        binaries.executable()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.kotlinx.html)
        }
    }
}

val webpack = tasks.named("jsBrowserProductionWebpack")

/** An unpacked extension at build/dist, loadable from chrome://extensions. */
val packageExtension by tasks.registering(Sync::class) {
    group = "build"
    description = "Assembles the unpacked Chromium extension into build/dist."
    from(layout.projectDirectory.dir("static"))
    from(webpack) { include("ianua.js") }
    into(layout.buildDirectory.dir("dist"))
}

/** The zip uploaded to the Chrome Web Store. */
val zipExtension by tasks.registering(Zip::class) {
    group = "build"
    description = "Zips build/dist for the Chrome Web Store."
    from(packageExtension)
    archiveFileName.set("ianua-extension.zip")
    destinationDirectory.set(layout.buildDirectory.dir("store"))
}
