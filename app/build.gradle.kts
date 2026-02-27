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
} else {
    logger.warn("keystore.properties not found at ${keystorePropertiesFile.absolutePath}")
}

android {
    namespace = "com.aiphotostudio.bgremover"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aiphotostudio.bgremover"
        minSdk = 26
        targetSdk = 36

        // ✅ Updated as requested
        versionCode = 46
        versionName = "7.6"

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

                if (
                    storeFileProp != null &&
                    storePasswordProp != null &&
                    keyAliasProp != null &&
                    keyPasswordProp != null
                ) {
                    val sFile = rootProject.file(storeFileProp)
                    if (sFile.exists()) {
                        storeFile = sFile
                        storePassword = storePasswordProp
                        keyAlias = keyAliasProp
                        keyPassword = keyPasswordProp
                    } else {
                        logger.warn("Keystore file not found at ${sFile.absolutePath}")
                    }
                } else {
                    logger.warn("keystore.properties missing one or more required fields: storeFile/storePassword/keyAlias/keyPassword")
                }
            }
        }
    }

    buildTypes {
        release {
            // ✅ TEMP FIX: Disable R8/minify so bundleRelease can build (your R8 step was failing)
            isMinifyEnabled = false
            isShrinkResources = false

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Keep signing logic
            signingConfig = signingConfigs.getByName("release")
            signingConfigs.findByName("release")?.let { releaseConfig ->
                if (releaseConfig.storeFile?.exists() == true) {
                    signingConfig = releaseConfig
                }
            }
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

    // Credentials & Google Auth
    implementation(libs.credentials.core)
    implementation(libs.credentials.play)
    implementation(libs.googleid.auth)
    implementation(libs.webkit.android)

    // Glide
    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)

    // ML Kit
    implementation(libs.mlkit.segmentation.selfie)

    // Firebase + Play Services Auth
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

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Security Fix for Guava vulnerability
    implementation(libs.guava)
}

// Fix for "Unable to delete directory" errors caused by .DS_Store or other background processes
tasks.named<Delete>("clean") {
    doFirst {
        val buildDir = layout.buildDirectory.get().asFile
        if (buildDir.exists()) {
            buildDir.deleteRecursively()
        }
    }
}