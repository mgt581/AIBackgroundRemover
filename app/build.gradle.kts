@file:Suppress("DEPRECATION")
plugins {
    id("com.android.application")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.bgremover"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aiphotostudio.bgremover"
        minSdk = 24
        targetSdk = 37
        versionCode = 7
        versionName = "4.1"

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
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        }
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.play.services.auth)

    implementation(libs.androidx.core.ktx.v1131)
    implementation("androidx.a:activity-compose:1.12.2")
    implementation("androidx.compose.ui:ui:1.10.1")
    implementation("androidx.compose.ui:ui-graphics:1.10.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.10.1")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.webkit:webkit:1.15.0")
    implementation("com.github.bumptech.glide:glide:5.0.5")
    annotationProcessor("com.github.bumptech.glide:compiler:5.0.5")
    debugImplementation("androidx.compose.ui:ui-tooling:1.10.1")
}
