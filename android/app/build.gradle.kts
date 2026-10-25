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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
tasks.named("preBuild") { dependsOn(privacyAssets) }
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
