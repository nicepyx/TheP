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

rootProject.name = "TheP"

include(
    ":app",
    ":core:model",
    ":core:database",
    ":core:data",
    ":core:designsystem",
    ":feature:home",
    ":feature:workout",
    ":feature:progress",
    ":feature:settings",
)
