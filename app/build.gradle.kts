import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.aiphotostudio.bgremover"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aiphotostudio.bgremover"
        minSdk = 26
        targetSdk = 35
        versionCode = 37
        versionName = "6.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(keystorePropertiesFile.inputStream())
    }

    signingConfigs {
        val ksFile = keystoreProperties["storeFile"]?.let { file(it as String) }
        val hasKeystore = ksFile?.exists() == true

        create("release") {
            if (hasKeystore) {
                keyAlias = keystoreProperties["keyAlias"] as String?
                keyPassword = keystoreProperties["keyPassword"] as String?
                storeFile = ksFile
                storePassword = keystoreProperties["storePassword"] as String?
            }
        }
        getByName("debug") {
            if (hasKeystore) {
                keyAlias = keystoreProperties["keyAlias"] as String?
                keyPassword = keystoreProperties["keyPassword"] as String?
                storeFile = ksFile
                storePassword = keystoreProperties["storePassword"] as String?
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

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
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
    constraints {
        implementation("com.google.guava:guava:32.1.2-android") {
            because("Earlier versions have a security vulnerability")
        }
    }
}
