plugins {
    id("com.diffplug.spotless") version "7.2.1"
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.hilt) apply false
}

spotless {
    kotlin {
        target("**/src/**/*.kt")
        targetExclude("**/build/**")
        ktfmt("0.59").kotlinlangStyle()
    }
    kotlinGradle {
        target("*.gradle.kts", "**/*.gradle.kts")
        targetExclude("**/build/**")
        ktfmt("0.59").kotlinlangStyle()
    }
}
