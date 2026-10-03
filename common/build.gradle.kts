plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.shilapi.xcertplay.host"
    compileSdk = 34

    defaultConfig {
        // KitKat port: Android 4.4.
        minSdk = 19
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(project(":shared"))
    implementation(libs.androidx.activity)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // Legacy multidex for KitKat (>64K method references).
    implementation("androidx.multidex:multidex:2.0.1")
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.12.2")
}
