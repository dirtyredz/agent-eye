plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ktlint)
}

allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.8.0")
        filter { exclude("**/build/**", "**/generated/**") }
    }
}

val bundletool by configurations.creating
dependencies { bundletool(libs.bundletool) }
tasks.register<Copy>("releaseTools") {
    from(bundletool)
    into(rootProject.layout.buildDirectory.dir("release-tools"))
}
