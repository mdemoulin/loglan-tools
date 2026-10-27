pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "loglan-android"

// drill: the drill itself, plain Kotlin, built and tested anywhere.
// app: the Android app around it, which needs the Android SDK.
include(":drill")
include(":app")
