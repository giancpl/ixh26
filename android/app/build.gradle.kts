import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}
val defaultServerUrl = (project.findProperty("SERVER_URL") as? String)
    ?: localProps.getProperty("server.url")
    ?: ""

android {
    namespace = "com.example.photoanchor"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.photoanchor"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
        buildConfigField("String", "DEFAULT_SERVER_URL", "\"$defaultServerUrl\"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    sourceSets.getByName("test").java.srcDir(rootProject.file("tests"))
}
dependencies {
    val camerax = "1.4.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    testImplementation("junit:junit:4.13.2")
}
