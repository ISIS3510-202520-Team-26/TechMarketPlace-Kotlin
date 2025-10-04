
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.techmarketplace"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.techmarketplace"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    // Compose
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }

    // Recomendado para Compose moderno
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    buildToolsVersion = "36.0.0"
}

dependencies {
    /* --------- Compose BOM --------- */
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.foundation)
    androidTestImplementation(platform(libs.androidx.compose.bom))

    /* --------- Compose --------- */
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.text)


    debugImplementation(libs.androidx.ui.tooling)
    implementation("com.google.android.material:material:1.13.0")


    /* --------- Core Android --------- */
    implementation(libs.androidx.core.ktx)

    /* --------- Tests --------- */
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    /* --------- Firebase Auth + Google Sign-In --------- */
    implementation(platform(libs.firebase.bom))        // <- BOM de Firebase con platform(...)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.play.services.auth)

}
