plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.numenlabs.zerophone.core.policy"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(projects.core.model)

    testImplementation(libs.junit)
}
