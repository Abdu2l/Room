plugins {
    id("com.android.application")

    id("org.jetbrains.kotlin.plugin.compose")
}

val lastFmApiKey = providers.gradleProperty("LASTFM_API_KEY").orElse("8d7764c5d8fc9663fdc32c4b61350e52").get()

android {
    namespace = "com.abdu2l.room"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.abdu2l.room"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        buildConfigField("String", "LASTFM_API_KEY", "\"${lastFmApiKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")

    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")


}
