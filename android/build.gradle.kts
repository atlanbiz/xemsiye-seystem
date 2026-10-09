buildscript {
    // The Android Gradle Plugin is put on the root classpath only when :app is part of the build
    // (see settings.gradle.kts), so the JVM-only :core module never needs Google's repository.
    // It lives in the same class loader as the Kotlin plugins declared below, which AGP requires.
    if (findProject(":app") != null) {
        repositories {
            google()
            mavenCentral()
        }
        dependencies {
            classpath(libs.android.gradlePlugin)
        }
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
