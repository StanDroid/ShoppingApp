import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    api(libs.coroutines.core)
    api(libs.paging.common)
    testImplementation(libs.junit)
}

ktlint {
    version.set(libs.versions.ktlint.get())
    filter {
        exclude("**/build/**", "**/generated/**")
    }
}
