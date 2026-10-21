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

rootProject.name = "ShotOCR"
include(":app")
include(":prefs")
include(":ocr-engine")
include(":screenshot-monitor")
include(":accounting")
include(":database")
include(":classification")
include(":translate-engine")
include(":translate-ui")
include(":llm-parser")
include(":shizuku")
include(":screen-capture")
include(":floating-button")
include(":shizuku-return")
