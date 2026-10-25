plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.3.10" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.8.0")
        filter { exclude("**/build/**", "**/generated/**") }
    }
}

val bundletool by configurations.creating
dependencies { bundletool("com.android.tools.build:bundletool:1.18.1") }
tasks.register<Copy>("releaseTools") {
    from(bundletool)
    into(rootProject.layout.buildDirectory.dir("release-tools"))
}
