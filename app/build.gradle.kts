import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()

if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
} else {
    println("WARNING: keystore.properties not found at ${keystorePropertiesFile.absolutePath}")
}

android {
    namespace = "com.aiphotostudio.bgremover"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aiphotostudio.bgremover"
        minSdk = 26
        targetSdk = 36
        versionCode = 37
        versionName = "6.7"

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

                if (storeFileProp != null && storePasswordProp != null && keyAliasProp != null && keyPasswordProp != null) {
                    val sFile = rootProject.file(storeFileProp)
                    if (sFile.exists()) {
                        storeFile = sFile
                        storePassword = storePasswordProp
                        keyAlias = keyAliasProp
                        keyPassword = keyPasswordProp
                    } else {
                        println("WARNING: Keystore file not found at ${sFile.absolutePath}")
                    }
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (signingConfigs.getByName("release").storeFile?.exists() == true) {
                signingConfig = signingConfigs.getByName("release")
            }
        }

        debug {
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

    // Firebase
    implementation(libs.play.services.auth)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
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
