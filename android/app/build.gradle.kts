import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-service:2.8.1")
    implementation("androidx.camera:camera-camera2:1.5.3")
    implementation("androidx.camera:camera-lifecycle:1.5.3")
    implementation("androidx.camera:camera-view:1.5.3")
    testImplementation("junit:junit:4.13.2")
}
