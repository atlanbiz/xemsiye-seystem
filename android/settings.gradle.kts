import java.util.Properties

pluginManagement {
    repositories {
        // Only Android artifacts come from Google's repository; everything else from Maven Central.
        google {
            content {
                includeGroupByRegex("com\\.android(\\..*)?")
                includeGroupByRegex("com\\.google\\.android(\\..*)?")
                includeGroupByRegex("androidx(\\..*)?")
                includeGroup("com.google.testing.platform")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android(\\..*)?")
                includeGroupByRegex("com\\.google\\.android(\\..*)?")
                includeGroupByRegex("androidx(\\..*)?")
                includeGroup("com.google.testing.platform")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "SolarPulse"

include(":core")

// :app needs the Android SDK (and Google's Maven repository). The pure-Kotlin :core module
// does not, so on machines without an SDK (CI containers, the simulator test job) only :core
// is configured and `./gradlew :core:test` still works. Android Studio always writes sdk.dir
// into local.properties, so the app module is included there automatically.
// Force-skip with -Psolarpulse.skipApp=true.
val localProperties = Properties().apply {
    val f = File(settingsDir, "local.properties")
    if (f.isFile) f.inputStream().use { load(it) }
}
val sdkDir: String? = localProperties.getProperty("sdk.dir")
    ?: System.getenv("ANDROID_HOME")
    ?: System.getenv("ANDROID_SDK_ROOT")
val skipApp = providers.gradleProperty("solarpulse.skipApp").orNull == "true"

if (!skipApp && sdkDir != null && File(sdkDir).isDirectory) {
    include(":app")
} else {
    logger.lifecycle("SolarPulse: Android SDK not found (sdk.dir / ANDROID_HOME) - configuring :core only.")
}
