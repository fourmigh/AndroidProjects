rootProject.name = "ComposeWidgets"
include(":widgets-theme")
project(":widgets-theme").projectDir = file("theme")
include(":widgets-segmented")
project(":widgets-segmented").projectDir = file("segmented")
include(":widgets-bottom-nav")
project(":widgets-bottom-nav").projectDir = file("bottom-nav")
