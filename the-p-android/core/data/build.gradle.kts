plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.thep.core.data"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":core:model"))
    testImplementation(libs.junit)
    implementation(project(":core:database"))
    implementation(libs.room.ktx)
    implementation(libs.datastore)
    implementation(libs.serialization.json)
    implementation(libs.coroutines)
}
