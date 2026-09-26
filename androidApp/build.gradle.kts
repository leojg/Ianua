plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "me.lgcode.ianua"
    compileSdk = 37

    defaultConfig {
        applicationId = "me.lgcode.ianua"
        minSdk = 26
        // 36 satisfies Google Play's current target requirement; move to 37 once its
        // behaviour changes have been reviewed.
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
    }

    // ADR-0004: identical in v0.1. Anything Play-only (e.g. Play Billing) goes in `play`
    // so it can never reach the F-Droid build.
    flavorDimensions += "store"
    productFlavors {
        create("play") { dimension = "store" }
        create("fdroid") { dimension = "store" }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    dependenciesInfo {
        // F-Droid cannot verify Google's encrypted dependency metadata blob.
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.coroutines.core)
}

/**
 * A package added to rules/blocked.json is only blocked from the first event, but the main
 * screen can only see it installed, and the service only hears it before the rule packs
 * load, if it is also in the manifest's <queries> and the accessibility config.
 */
val verifyBlockedPackages by tasks.registering {
    group = "verification"
    description = "Checks that every package in rules/blocked.json is in <queries> and the accessibility config."
    val rules = rootProject.layout.projectDirectory.file("rules/blocked.json")
    val manifest = layout.projectDirectory.file("src/main/AndroidManifest.xml")
    val config = layout.projectDirectory.file("src/main/res/xml/accessibility_service_config.xml")
    inputs.files(rules, manifest, config)
    doLast {
        val packages = Regex("\"androidPackages\"\\s*:\\s*\\[([^\\]]*)]")
            .findAll(rules.asFile.readText())
            .flatMap { Regex("\"([^\"]+)\"").findAll(it.groupValues[1]).map { m -> m.groupValues[1] } }
            .toSet()
        val manifestText = manifest.asFile.readText()
        val configText = config.asFile.readText()
        val missing = packages.filter { "<package android:name=\"$it\"" !in manifestText || it !in configText }
        check(packages.isNotEmpty()) { "no androidPackages found in rules/blocked.json" }
        check(missing.isEmpty()) { "Add to <queries> in AndroidManifest.xml and to accessibility_service_config.xml: $missing" }
    }
}

tasks.named("check") { dependsOn(verifyBlockedPackages) }
