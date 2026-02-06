plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android") version "1.9.0" // Kotlin version compatible with Compose
    id("com.google.gms.google-services") // if using Firebase
}

android {
    namespace = "com.aiphotostudio.bgremover"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aiphotostudio.bgremover"
        minSdk = 24
        targetSdk = 36
        versionCode = 35
        versionName = "6.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.6.0" // match Compose version
    }

    packagingOptions {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
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
}

dependencies {
    // Compose
    implementation("androidx.compose.ui:ui:1.6.0")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Kotlin stdlib
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")

    // Google Play / Firebase (aligned versions)
    implementation("com.google.android.gms:play-services-auth:21.5.0")
    implementation("com.google.firebase:firebase-auth:22.2.0")
    implementation("com.google.firebase:firebase-analytics-ktx:22.2.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.6.0")
}