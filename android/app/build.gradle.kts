import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
val releaseVersion = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
require(
    Regex(
        "(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"
    ).matches(releaseVersion.getProperty("versionName"))
)
require(releaseVersion.getProperty("versionCode").toInt() in 1..2100000000)
val privacyAssets = tasks.register<Copy>("generatePrivacyAssets") {
    from(rootProject.file("../docs/PRIVACY.md"))
    into(layout.buildDirectory.dir("generated/privacyAssets"))
}

// The updater's signer pin comes only from the tracked public certificate record; a missing or
// malformed record fails the build (no second hand-written pin).
val signingCertificate = rootProject.file("../src/android/releases/signing-certificate.json")
val updatePinAssets = tasks.register("generateUpdatePinAsset") {
    val output = layout.buildDirectory.dir("generated/updatePinAssets")
    inputs.file(signingCertificate)
    inputs.property("versionCode", releaseVersion.getProperty("versionCode"))
    outputs.dir(output)
    doLast {
        @Suppress("UNCHECKED_CAST")
        val pin = groovy.json.JsonSlurper().parse(signingCertificate) as Map<String, Any?>
        val sha = pin["sha256"] as? String
        val first = pin["firstVersionCode"] as? Int
        require(
            pin.keys == setOf("applicationId", "alias", "firstVersionCode", "sha256") &&
                pin["applicationId"] == "local.agenteye" && pin["alias"] == "agent-eye" &&
                sha != null && Regex("[a-f0-9]{64}").matches(sha) &&
                first != null && first in 1..releaseVersion.getProperty("versionCode").toInt()
        ) { "signing-certificate.json is missing or invalid; refusing to package an update pin" }
        val dir = output.get().asFile
        dir.mkdirs()
        dir.resolve("update-signer.txt").writeText(sha + "\n")
    }
}
android {
    buildFeatures { compose = true }
    namespace = "local.agenteye"
    compileSdk = 36
    defaultConfig {
        applicationId = "local.agenteye"
        minSdk = 24
        targetSdk = 36
        versionCode = releaseVersion.getProperty("versionCode").toInt()
        versionName = releaseVersion.getProperty("versionName")
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/privacyAssets"))
    sourceSets.getByName(
        "main"
    ).assets.srcDir(layout.buildDirectory.dir("generated/updatePinAssets"))
    testOptions.unitTests.all {
        // Shared schema-1 fixtures, also consumed by the Node publisher tests.
        it.systemProperty(
            "agenteye.updateFixtures",
            rootProject.file(
                "../src/android/releases/artifacts/fixtures/update-schema1"
            ).absolutePath
        )
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
tasks.named("preBuild") { dependsOn(privacyAssets, updatePinAssets) }
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
}
