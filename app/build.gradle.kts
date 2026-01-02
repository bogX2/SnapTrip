import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Add the Google services Gradle plugin
    id("com.google.gms.google-services")

    id("kotlin-kapt")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}
// Carica la chiave da local.properties

android {
    namespace = "com.example.snaptrip"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.snaptrip"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 1. Per il Manifest (Google Maps)
        manifestPlaceholders["MAPS_API_KEY"] = localProperties.getProperty("MAPS_API_KEY") ?: ""

        // 2. Per il Codice Kotlin (Places SDK) - AGGIUNGI QUESTO:
        buildConfigField("String", "MAPS_API_KEY", "\"${localProperties.getProperty("MAPS_API_KEY")}\"")

        buildConfigField("String", "OPENWEATHER_KEY", "\"${localProperties.getProperty("OPENWEATHER_API_KEY")}\"")

        // 2. AGGIUNGI QUESTA PARTE PER GEMINI
        buildConfigField("String", "GEMINI_API_KEY", "\"${localProperties.getProperty("GEMINI_API_KEY")}\"")



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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // AGGIUNTA: Libreria con tutte le icone Material
    implementation("androidx.compose.material:material-icons-extended")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.android.libraries.places:places:3.3.0")


    // Navigazione e ViewModel
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")

    // --- FIREBASE---
    implementation(platform("com.google.firebase:firebase-bom:34.6.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    
    // Coil per caricare immagini da URL
    implementation("io.coil-kt:coil-compose:2.4.0")

    // Reorderable List (Drag & Drop)
    implementation("org.burnoutcrew.composereorderable:reorderable:0.9.6")

    // ROOM DATABASE
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    kapt("androidx.room:room-compiler:$room_version") // Use 'ksp' if you migrated, otherwise 'kapt'

    // GSON (For converting Lists to String in DB)
    implementation("com.google.code.gson:gson:2.10.1")

    // ML Kit Image Labeling
    implementation("com.google.mlkit:image-labeling:17.0.7")

    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    // api gemini

    // Utile per usare il Geocoder
    implementation("com.google.android.gms:play-services-maps:18.2.0")



    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}