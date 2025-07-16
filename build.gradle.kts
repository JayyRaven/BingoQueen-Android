// This file is typically located at app/build.gradle.kts
// It tells Android Studio what external libraries your app needs to function.

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Add the Google services plugin for Firebase
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.bingoqueen"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.bingoqueen"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    
    kotlinOptions {
        jvmTarget = "1.8"
    }

    // Enable View Binding to easily access UI elements from code
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // Standard Android libraries
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.gridlayout:gridlayout:1.0.0") // For the Bingo board

    // Firebase Bill of Materials (BoM) - manages versions for all Firebase libraries
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))

    // Firebase libraries for Authentication and Firestore Database
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")

    // Kotlin Coroutines for managing background tasks
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3") // For Firebase integration

    // Lifecycle components for observing data changes
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
}
