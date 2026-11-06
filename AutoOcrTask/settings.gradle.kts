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
    versionCatalogs {
        create("libs") {
            from(files("../ShotOCR/gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "AutoOcrTask"
include(":app")

listOf(
    "shizuku",
    "shizuku-automation",
    "shizuku-return",
    "prefs",
    "expandable-card",
    "floating-button",
    "segmented-control"
).forEach { name ->
    include(":$name")
    project(":$name").projectDir = file("../ShotOCR/$name")
}

include(":ocr-api")
project(":ocr-api").projectDir = file("../OnDeviceOcr/api")
