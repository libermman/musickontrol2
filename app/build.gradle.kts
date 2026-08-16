plugins {
    id("com.android.application")
    id("kotlin-android")
    kotlin("plugin.serialization") version "1.9.22"
}

android {
    namespace = "com.dnc1981.musickontrol"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dnc1981.musickontrol"
        minSdk = 31
        targetSdk = 35
        versionCode = 10
        versionName = "1.1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += "-P"
        freeCompilerArgs += "plugin:androidx.compose.compiler.plugins.kotlin:suppressKotlinVersionCompatibilityCheck=1.9.23"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    // ✅ CORE ANDROID
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // ✅ COMPOSE - Actualizado para Kotlin 1.9.23
    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.1.2")
    implementation("androidx.compose.material:material-icons-extended:1.6.0")
    implementation("androidx.compose.foundation:foundation:1.6.0")
    implementation("androidx.compose.runtime:runtime:1.6.0")

    // ✅ EXOPLAYER / MEDIA3 (CON HLS Y COMPOSITE)
    implementation("androidx.media3:media3-exoplayer:1.1.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.1.1")
    implementation("androidx.media3:media3-ui:1.1.1")
    implementation("androidx.media3:media3-common:1.1.1")
    implementation("androidx.media3:media3-datasource:1.1.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.1.1")

    // ✅ MEDIASESSION Y NOTIFICACIONES
    implementation("androidx.media:media:1.6.0")
    implementation("androidx.media3:media3-session:1.1.1")

    // ✅ DOCUMENTFILE (para acceso a archivos)
    implementation("androidx.documentfile:documentfile:1.0.1")

    // ✅ GSON (para serialización JSON)
    implementation("com.google.code.gson:gson:2.10.1")

    // ✅ GUAVA (para ListenableFuture y Futures)
    implementation("com.google.guava:guava:32.1.3-android")

    // ✅ OKHTTP (para networking con headers personalizados)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ✅ PERMISSIONS
    implementation("com.google.accompanist:accompanist-permissions:0.33.2-alpha")

    // ✅ COROUTINES
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // ✅ TESTING
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.01.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // ✅ SERIALIZACION
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // ✅ DEBUG
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
