plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { jvmToolchain(21) ; compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
    // Reference implementation, only used to cross-check the port.
    testImplementation(libs.adhan.reference)
}

tasks.test { useJUnitPlatform() }
