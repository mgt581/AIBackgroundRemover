import java.io.File
import java.io.FileInputStream
import java.util.Properties
import com.android.build.api.dsl.ApplicationExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

// Extract function: load properties only when a file exists
fun loadPropertiesIfExists(file: File): Properties = Properties().apply {
    if (file.exists()) {
        FileInputStream(file).use { input -> load(input) }
    }
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProps: Properties = loadPropertiesIfExists(keystorePropertiesFile)

// Introduce variable: compute once and reuse
val releaseKeystoreFile: File? =
    keystoreProps.getProperty("storeFile")
        ?.let(rootProject::file)
        ?.takeIf(File::exists)

// Introduce constants: keep config values readable and consistent
val appCompileSdk = 36
val appMinSdk = 23
val appTargetSdk = 36
val javaCompat = JavaVersion.VERSION_17

configure<ApplicationExtension> {
    namespace = "com.aiphotostudio.bgremover"
    compileSdk = appCompileSdk

    defaultConfig {
        applicationId = "com.aiphotostudio.bgremover"
        minSdk = appMinSdk
        targetSdk = appTargetSdk
        versionCode = 98
        versionName = "9.8"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            // Simplify: configure only when the keystore file is present and valid
            if (releaseKeystoreFile != null) {
                storeFile = releaseKeystoreFile
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            ndk { debugSymbolLevel = "full" }

            // Move/simplify: a single source of truth for choosing signing config
            signingConfig =
                if (releaseKeystoreFile != null) signingConfigs.getByName("release")
                else signingConfigs.getByName("debug")
        }

        getByName("debug") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
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
        sourceCompatibility = javaCompat
        targetCompatibility = javaCompat
    }
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
    implementation(libs.play.services.base)
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

// Simplify: Delete task already supports deleting providers/dirs
tasks.named<Delete>("clean") {
    delete(layout.buildDirectory)
}
