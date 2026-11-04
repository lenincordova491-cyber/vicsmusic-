pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "vicsmusic"

// App entry point
include(":app")

// Core modules
include(":core:common")
include(":core:model")
include(":core:domain")
include(":core:data")
include(":core:database")
include(":core:network")
include(":core:datastore")
include(":core:player")
include(":core:designsystem")
include(":core:ui")

// Source modules (Music source plugins)
include(":source:api")
include(":source:local")
include(":source:remote")

// Feature modules
include(":feature:home")
include(":feature:search")
include(":feature:library")
include(":feature:player")
include(":feature:playlist")
include(":feature:album")
include(":feature:artist")
include(":feature:settings")
include(":feature:onboarding")
