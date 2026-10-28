plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.maqdi.core"
    compileSdk = 34
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    api(platform("androidx.compose:compose-bom:2024.09.00"))
    api("androidx.compose.ui:ui")
    api("androidx.compose.material3:material3")
    api("androidx.compose.material:material-icons-core")
    api("androidx.compose.material:material-icons-extended")
    api("androidx.activity:activity-compose:1.9.2")
    api("androidx.navigation:navigation-compose:2.8.0")
    api("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    api("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    api("androidx.core:core-ktx:1.13.1")

    api(platform("com.google.firebase:firebase-bom:33.3.0"))
    api("com.google.firebase:firebase-auth")
    api("com.google.android.gms:play-services-base:18.5.0") // SignInButton الرسمي من Google
    api("com.google.firebase:firebase-firestore")
    api("com.google.firebase:firebase-functions")
    api("com.google.firebase:firebase-storage")
    api("com.google.firebase:firebase-messaging")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    api("androidx.credentials:credentials:1.3.0")
    api("androidx.credentials:credentials-play-services-auth:1.3.0")
    api("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    api("io.coil-kt:coil-compose:2.7.0")
}
