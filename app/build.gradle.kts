plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.numenlabs.zerophone"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.numenlabs.zerophone"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

// compileSdk is pinned to 36.1; cap transitive androidx.core/lifecycle to
// versions whose minCompileSdk <= 36 (newer ones require compileSdk 37).
configurations.all {
    resolutionStrategy.force(
        "androidx.core:core:1.18.0",
        "androidx.lifecycle:lifecycle-runtime:2.10.0",
        "androidx.lifecycle:lifecycle-runtime-android:2.10.0",
        "androidx.lifecycle:lifecycle-runtime-ktx:2.10.0",
        "androidx.lifecycle:lifecycle-runtime-ktx-android:2.10.0",
        "androidx.lifecycle:lifecycle-runtime-compose:2.10.0",
        "androidx.lifecycle:lifecycle-runtime-compose-android:2.10.0"
    )
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.policy)
    implementation(projects.core.ui)
    implementation(projects.feature.home)
    implementation(projects.feature.allowlist)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}