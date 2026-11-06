import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("../keystore/keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "org.caojun.autoocrtask"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.caojun.autoocrtask"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("shared") {
            storeFile = rootProject.file(
                keystoreProperties.getProperty("storeFile", "../keystore/shared.p12")
            )
            storePassword = keystoreProperties.getProperty("storePassword", "android")
            keyAlias = keystoreProperties.getProperty("keyAlias", "shared")
            keyPassword = keystoreProperties.getProperty("keyPassword", "android")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))

    implementation(project(":shizuku"))
    implementation(project(":shizuku-automation"))
    implementation(project(":shizuku-return"))
    implementation(project(":ocr-api"))
    implementation(project(":prefs"))
    implementation(project(":expandable-card"))
    implementation(project(":floating-button"))
    implementation(project(":segmented-control"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.bundles.compose.ui)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    debugImplementation(libs.compose.tooling)
}
