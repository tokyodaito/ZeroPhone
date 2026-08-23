plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.desktop)
}

// Compose Desktop registers its own `run`/packaging tasks, so the plain
// `application` plugin is intentionally NOT applied here: its `run` task
// collides with the one from the Compose plugin.
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

compose.desktop {
    application {
        mainClass = "com.numenlabs.zerophone.desktop.DesktopAppKt"
        nativeDistributions {
            packageName = "zerophone-desktop"
            packageVersion = project.version.toString()
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
            )
            // jpackage on macOS rejects versions whose first segment is 0
            // ("The first number in an app-version cannot be zero or negative").
            macOS { packageVersion = "1.0.0" }
        }
    }
}

dependencies {
    api(projects.core.model)
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}
