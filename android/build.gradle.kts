import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint) apply false
}

apply(plugin = "org.jlleitschuh.gradle.ktlint")

extensions.configure<KtlintExtension>("ktlint") {
    android.set(true)
    outputToConsole.set(true)
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    extensions.configure<KtlintExtension>("ktlint") {
        android.set(true)
        outputToConsole.set(true)
    }
}
