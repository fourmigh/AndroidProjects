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
include(":screenshot-monitor")
include(":accounting")
include(":database")
include(":classification")
include(":translate-ui")
include(":llm-parser")
include(":shizuku")
include(":screen-capture")
include(":floating-button")
include(":shizuku-return")
include(":segmented-control")
include(":shizuku-automation")
include(":expandable-card")

// 三个本地 AI 能力由独立工程提供，这里只引用它们的客户端契约模块。
include(":ocr-api")
project(":ocr-api").projectDir = file("../OnDeviceOcr/api")
include(":translate-api")
project(":translate-api").projectDir = file("../OnDeviceTranslate/api")
include(":llm-api")
project(":llm-api").projectDir = file("../OnDeviceLlm/api")
