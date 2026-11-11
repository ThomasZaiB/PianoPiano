plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.tuapp.piano"
    compileSdk = 35
    // NDK fijo para builds reproducibles (instálalo desde SDK Manager > SDK Tools > NDK side by side)
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.tuapp.piano"
        // 26 = Android 8.0: primera versión con AAudio. Oboe cae a OpenSL ES por debajo,
        // pero la latencia ahí no cumple el objetivo, así que no vale la pena soportarlo.
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // arm64 = casi todos los teléfonos actuales; armeabi-v7a = gama baja antigua;
            // x86_64 = emulador. Quita los que no necesites para reducir el APK.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-Wall", "-Wextra")
                arguments += listOf(
                    "-DANDROID_STL=c++_shared", // obligatorio: Oboe vía Prefab se distribuye con c++_shared
                    "-DANDROID_ARM_NEON=ON"
                )
            }
        }
    }

    buildTypes {
        debug {
            externalNativeBuild {
                cmake { arguments += "-DCMAKE_BUILD_TYPE=Debug" }
            }
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            externalNativeBuild {
                cmake { arguments += "-DCMAKE_BUILD_TYPE=Release" }
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        compose = true
        prefab = true // permite que CMake encuentre Oboe con find_package(oboe)
    }

    // Los .sf2 ya vienen comprimidos/son grandes: no recomprimir permite
    // abrirlos con AAssetManager sin descomprimir a memoria.
    androidResources {
        noCompress += "sf2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

ksp {
    // Room exporta el esquema para poder escribir migraciones en el futuro
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    // Núcleo Android + ciclo de vida
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // Compose (versiones alineadas por el BOM)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Corrutinas / Flow
    implementation(libs.kotlinx.coroutines.android)

    // Hilt (DI)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room (persistencia)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Oboe (audio de baja latencia, consumido desde C++ vía Prefab)
    implementation(libs.oboe)

    // MIDI: exportador SMF propio en data/midi/MidiFileWriter.kt (sin dependencias externas).

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
