import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services") apply false
}

// Only apply Google Services after the developer adds the local Firebase config.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) propertiesFile.inputStream().use { load(it) }
}

val releaseSigning = mapOf(
    "storeFile" to localProperties.getProperty("RELEASE_STORE_FILE"),
    "storePassword" to localProperties.getProperty("RELEASE_STORE_PASSWORD"),
    "keyAlias" to localProperties.getProperty("RELEASE_KEY_ALIAS"),
    "keyPassword" to localProperties.getProperty("RELEASE_KEY_PASSWORD")
)
val hasAnyReleaseSigningValue = releaseSigning.values.any { !it.isNullOrBlank() }
val hasCompleteReleaseSigning = releaseSigning.values.all { !it.isNullOrBlank() }
require(!hasAnyReleaseSigningValue || hasCompleteReleaseSigning) {
    "La firma release está incompleta. Configura RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS y RELEASE_KEY_PASSWORD en local.properties, o elimina los cuatro."
}
val releaseKeystore = releaseSigning["storeFile"]?.let { rootProject.file(it) }
if (hasCompleteReleaseSigning) {
    require(releaseKeystore?.isFile == true) { "No se encuentra el keystore release indicado en local.properties." }
}

fun buildConfigString(name: String, fallbackName: String? = null): String {
    val rawValue = localProperties.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: fallbackName?.let { localProperties.getProperty(it) }
        ?: ""
    val value = rawValue
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    return "\"$value\""
}

android {
    namespace = "com.pawly.app"
    // Keep the compile SDK configurable for CI while defaulting to the current dependency requirement.
    compileSdk = providers.gradleProperty("pawlyCompileSdk").orNull?.toInt() ?: 37

    defaultConfig {
        applicationId = "com.pawly.app"
        minSdk = 26
        targetSdk = providers.gradleProperty("pawlyTargetSdk").orNull?.toInt() ?: 36
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", buildConfigString("CLOUDINARY_CLOUD_NAME"))
        // Separate presets let Cloudinary enforce different allowed formats for images and videos.
        // The legacy key remains a fallback so existing local.properties files keep working.
        buildConfigField("String", "CLOUDINARY_IMAGE_UPLOAD_PRESET", buildConfigString("CLOUDINARY_IMAGE_UPLOAD_PRESET", "CLOUDINARY_UPLOAD_PRESET"))
        buildConfigField("String", "CLOUDINARY_VIDEO_UPLOAD_PRESET", buildConfigString("CLOUDINARY_VIDEO_UPLOAD_PRESET", "CLOUDINARY_UPLOAD_PRESET"))
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            if (hasCompleteReleaseSigning) {
                storeFile = releaseKeystore
                storePassword = releaseSigning["storePassword"]
                keyAlias = releaseSigning["keyAlias"]
                keyPassword = releaseSigning["keyPassword"]
            }
        }
    }

    buildTypes {
        getByName("release") {
            // CI remains unsigned. Local builds become signed when all four
            // RELEASE_* values are supplied in the developer's local.properties.
            if (hasCompleteReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.10.2")


    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-messaging")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("junit:junit:4.13.2")

    // Firebase community backend (Authentication and Firestore; no Firebase Storage).
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
}
