
import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties




fun String.escapeForBuildConfig(): String = replace("\"", "\\\"")

val localProperties = gradleLocalProperties(rootDir, providers)
val defaultApiBaseUrl = localProperties.getProperty("api.baseUrl") ?: "http://3.19.208.242:8000/v1/"
val debugApiBaseUrl = localProperties.getProperty("api.baseUrl.debug") ?: defaultApiBaseUrl
val releaseApiBaseUrl = localProperties.getProperty("api.baseUrl.release") ?: defaultApiBaseUrl
val mapsApiKey = localProperties.getProperty("maps.apiKey") ?: ""

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.techmarketplace"
    compileSdk = 34

    defaultConfig {
        manifestPlaceholders += mapOf("MAPS_API_KEY" to mapsApiKey)
        applicationId = "com.techmarketplace"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", "\"${defaultApiBaseUrl.escapeForBuildConfig()}\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"${debugApiBaseUrl.escapeForBuildConfig()}\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "API_BASE_URL", "\"${releaseApiBaseUrl.escapeForBuildConfig()}\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Compose
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get() }

    // Recomendado para Compose moderno
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {

    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.runtime.saveable)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.core.ktx)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.crashlytics.buildtools)
    implementation(libs.play.services.auth)
    implementation(libs.play.services.location)

    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

    implementation(libs.coil.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.material)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
