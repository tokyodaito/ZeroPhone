// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// compileSdk is pinned to 36.1 across all modules; cap transitive androidx.core/lifecycle to
// versions whose minCompileSdk <= 36 (newer ones require compileSdk 37).
subprojects {
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
}
