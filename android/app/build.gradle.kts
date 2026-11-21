plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.example.photoanchor"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.photoanchor"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
    buildFeatures { compose = true }
    buildFeatures { buildConfig = true }
    buildTypes {
        debug {
            val authorityPin = providers.gradleProperty("photoAuthoritySpki").orElse("").get()
            require(authorityPin.matches(Regex("[A-Za-z0-9+/=]*")))
            buildConfigField("String", "AUTHORITY_SPKI", "\"$authorityPin\"")
            buildConfigField("String", "API_URL", "\"http://127.0.0.1:8080\"")
            buildConfigField("String", "RELAY_URL", "\"ws://127.0.0.1:7777\"")
        }
        release {
            buildConfigField("String", "AUTHORITY_SPKI", "\"\"")
            // No public services or cleartext endpoints are configured for release.
            buildConfigField("String", "API_URL", "\"\"")
            buildConfigField("String", "RELAY_URL", "\"\"")
        }
    }
    sourceSets["test"].resources.srcDir("../../contracts/fixtures")
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    sourceSets.getByName("test").java.srcDir(rootProject.file("tests"))
}
dependencyLocking { lockAllConfigurations() }
dependencies {
    implementation("org.nostrdevkit:nostr-sdk:0.45.1")
    // The official binding exposes JNA types in its public interface.
    implementation("net.java.dev.jna:jna:5.17.0@aar")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
    // The same official binding with desktop native libraries enables real Schnorr
    // checks in JVM tests; Android builds continue to package only the Android AAR.
    testRuntimeOnly("org.nostrdevkit:nostr-sdk-jvm:0.45.1") { isTransitive = false }
    testRuntimeOnly("net.java.dev.jna:jna:5.17.0@jar")
    testImplementation("org.json:json:20240303")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    constraints {
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0") {
            version { strictly("1.9.0") }
        }
    }
    val camerax = "1.4.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("org.bouncycastle:bcprov-jdk18on:1.87")
}
