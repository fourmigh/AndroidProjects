plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.caojun.ai.translate.hub.llama"
    compileSdk = libs.versions.compileSdk.get().toInt()
    ndkVersion = "27.0.12077973"

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DLLAMA_CPP_DIR=${rootProject.projectDir}/third_party/llama.cpp",
                    "-DANDROID_STL=c++_shared",
                    // 目标设备：骁龙 8 Elite（ARMv9，支持 dotprod / i8mm / fp16）。
                    // 硬编指令集换来 3-6 倍解码速度；老设备会 SIGILL，按需改回 armv8.2-a+dotprod。
                    "-DGGML_CPU_ARM_ARCH=armv8.2-a+dotprod+i8mm+fp16",
                )
                cppFlags += listOf("-std=c++17", "-fexceptions", "-frtti")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    api(project(":engine-core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
