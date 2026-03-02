import java.io.File
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aiphotostudiobgremover"
        minSdk = 23
        targetSdk = 35

        versionCode = 97
        versionName = "9.7"

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
                    val kFile = rootProject.file(storeFileProp)
                    if (kFile.exists()) {
                        storeFile = kFile
                        storePassword = storePasswordProp
                        keyAlias = keyAliasProp
                        keyPassword = keyPasswordProp
                    }
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            ndk {
                debugSymbolLevel = "full"
            }

            // Consolidate signing configuration
            val releaseSigningConfig = signingConfigs.findByName("release")
            if (releaseSigningConfig?.storeFile != null && releaseSigningConfig.storeFile!!.exists()) {
                signingConfig = releaseSigningConfig
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }

        debug {
            isDebuggable = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
    }

    packaging {
        resources {
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
            excludes.add("META-INF/DEPENDENCIES")
            pickFirsts.add("META-INF/androidx.localbroadcastmanager_localbroadcastmanager.version")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // Manually adding known missing references from the previous error or toml
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation(libs.google.material)

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation("androidx.vectordrawable:vectordrawable-animated:1.2.0")
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)

    // Auth & Utilities
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services-auth)
    implementation(libs.googleid)
    implementation(libs.androidx.webkit)
    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")

    // Firebase
    implementation(libs.play.services.auth)
    implementation("com.google.android.gms:play-services-base:18.5.0")
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-appcheck")
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.appcheck.debug)
    implementation("com.google.firebase:firebase-analytics")

    // Testing & Guava
    testImplementation(libs.junit)
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.test.espresso:espresso-core:3.6.1")
    implementation("com.google.guava:guava:33.2.1-android")
}

tasks.named<Delete>("clean") {
    doFirst {
        val buildDir = layout.buildDirectory.get().asFile
        if (buildDir.exists()) {
            buildDir.deleteRecursively()
        }
    }
}
