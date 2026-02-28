import java.io.File
import java.io.FileInputStream
import java.util.Properties 

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties: Properties = Properties()

if (keystorePropertiesFile.exists()) {
    FileInputStream(keystorePropertiesFile).use { input ->
        keystoreProperties.load(input)
    }
}

android {
    namespace = "com.aiphotostudio.bgremover"
    // Using API 35 (Android 15) for stability and maximum device compatibility
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aiphotostudio.bgremover"
        minSdk = 23
        targetSdk = 36

        versionCode = 90
        versionName = "9.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                val storeFileProp = keystoreProperties["storeFile"] as? String
                val storePasswordProp = keystoreProperties["storePassword"] as? String
                val keyAliasProp = keystoreProperties["keyAlias"] as? String
                val keyPasswordProp = keystoreProperties["keyPassword"] as? String

                if (storeFileProp != null) {
                    storeFile = rootProject.file(storeFileProp)
                    storePassword = storePasswordProp
                    keyAlias = keyAliasProp
                    keyPassword = keyPasswordProp
                }
            }
        }
    }

    buildTypes {
        release {
            // Fixes "Large APK size" and "Missing deobfuscation file"
            isMinifyEnabled = true
            isShrinkResources = true
            
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Fixes "Missing native debug symbols"
            ndk {
                debugSymbolLevel = "full"
            }

            signingConfig = signingConfigs.getByName("release")
        }

        debug {
            isDebuggable = true
            signingConfigs.findByName("debug")?.let { debugConfig ->
                signingConfig = debugConfig
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            pickFirsts += "META-INF/androidx.localbroadcastmanager_localbroadcastmanager.version"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.animated.vector.drawable)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)

    // Auth & Utilities
    implementation(libs.credentials.core)
    implementation(libs.credentials.play)
    implementation(libs.googleid.auth)
    implementation(libs.webkit.android)
    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)
    implementation(libs.mlkit.segmentation.selfie)

    // Firebase
    implementation(libs.play.services.auth)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.appcheck)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.appcheck.debug)
    implementation(libs.google.firebase.analytics)

    // Testing & Guava
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.guava)
}

tasks.named<Delete>("clean") {
    doFirst {
        val buildDir = layout.buildDirectory.get().asFile
        if (buildDir.exists()) {
            buildDir.deleteRecursively()
        }
    }
}
