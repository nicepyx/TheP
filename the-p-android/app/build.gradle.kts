plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.thep.android"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        applicationId = "com.thep.android"
        targetSdk = 36
        versionCode = 1
        versionName = "5.0.0-alpha01"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        debug { applicationIdSuffix = ".dev" }
        release { isMinifyEnabled = false }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":core:model"))
    testImplementation(libs.junit)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.tooling)
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.navigation.compose)
    implementation(libs.coroutines)
    implementation(libs.datastore)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.androidx.test)
    debugImplementation(libs.compose.test.manifest)
    implementation(project(":feature:home"))
    implementation(project(":feature:workout"))
    implementation(project(":feature:progress"))
    implementation(project(":feature:settings"))
}
