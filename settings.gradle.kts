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

rootProject.name = "ClaudeMeter"
include(":shared")
include(":androidApp")

project(":androidApp").projectDir = file("android/app")
