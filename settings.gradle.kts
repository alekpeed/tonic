pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "tonic"

include(
    ":app",
    ":core:model",
    ":core:curriculum",
    ":core:engine",
    ":core:audio",
    ":core:data",
    ":core:ui",
    ":feature:diagnostic",
    ":feature:practice",
    ":feature:progress",
    ":feature:settings",
)
