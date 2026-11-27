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

rootProject.name = "CupFox"
include(":app")
include(":traffic")
include(":player")
include(":widgets-theme")
project(":widgets-theme").projectDir = file("../ComposeWidgets/theme")
include(":widgets-segmented")
project(":widgets-segmented").projectDir = file("../ComposeWidgets/segmented")
include(":widgets-bottom-nav")
project(":widgets-bottom-nav").projectDir = file("../ComposeWidgets/bottom-nav")
