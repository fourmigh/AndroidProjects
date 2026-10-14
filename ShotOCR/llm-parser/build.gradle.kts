plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.caojun.shotocr.llm"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters += listOf("arm64-v8a")
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

    androidResources {
        noCompress.add("litertlm")
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "native/**"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    implementation(project(":prefs"))
    implementation(libs.litertlm.android)

    testImplementation(libs.junit)
    // Android 单元测试使用 android.jar 里被 stub 的 org.json，这里用真实实现替代。
    testImplementation("org.json:json:20240303")
}

// 构建期校验：LLM 模型是否随 APK 打包（缺失时给出明确提示，避免"装了却没模型"）。
val checkLlmModel = tasks.register("checkLlmModel") {
    val modelFile = layout.projectDirectory.file(
        "src/main/assets/llm/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm"
    )
    doFirst {
        val file = modelFile.asFile
        if (!file.exists() || file.length() == 0L) {
            logger.warn(
                "LLM model asset is missing: ${file.absolutePath}\n" +
                    "Download qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm and place it there. " +
                    "See llm-parser/src/main/assets/llm/README.txt."
            )
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(checkLlmModel) }
