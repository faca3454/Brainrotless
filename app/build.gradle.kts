plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// En CI se pasan por variables de entorno; local queda como "dev"
val appVersionName: String = System.getenv("VERSION_NAME") ?: "dev"
val appVersionCode: Int = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
val keystoreFile: String? = System.getenv("KEYSTORE_FILE")

android {
    namespace = "com.brainrotless.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.brainrotless.app"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        create("release") {
            if (keystoreFile != null) {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = "brainrotless"
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Sin clave de release (builds de prueba) se firma con la de debug
            signingConfig = signingConfigs.getByName(if (keystoreFile != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
}
