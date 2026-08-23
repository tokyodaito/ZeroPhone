plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

application {
    mainClass = "com.numenlabs.zerophone.sync.server.MainKt"
}

// Self-contained runnable server jar (`java -jar zerophone-sync-server-all.jar
// --issue-code` / `java -jar zerophone-sync-server-all.jar --port=8080`).
// Service descriptors of bundled dependencies are appended (SLF4J
// provider discovery); no ServiceLoader-based engine discovery is
// relied upon — the CIO engine is selected explicitly in MainKt.
tasks.shadowJar {
    group = "build"
    description = "Builds the self-contained runnable sync server jar."
    archiveBaseName = "zerophone-sync-server"
    archiveClassifier = "all"
    mergeServiceFiles()
    manifest {
        attributes("Main-Class" to application.mainClass.get())
    }
}

dependencies {
    api(projects.core.model)
    implementation(platform(libs.ktor.bom))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.websockets)
    implementation(libs.logback.classic)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.core)
    testImplementation(libs.ktor.client.cio)
}
