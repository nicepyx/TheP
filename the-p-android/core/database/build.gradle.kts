plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "com.thep.core.database"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        javaCompileOptions {
            annotationProcessorOptions { arguments["room.schemaLocation"] = "$projectDir/schemas" }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":core:model"))
    testImplementation(libs.junit)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)
    implementation(libs.serialization.json)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.coroutines.test)
}
