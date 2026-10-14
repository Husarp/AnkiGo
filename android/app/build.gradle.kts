import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The app's version: the one place it is written. versionCode must grow with every release or Android
// refuses the update, so it comes from the version: 0.1.0 -> 100, 1.2.3 -> 10203.
val appVersion = "0.4.2"
val (vMajor, vMinor, vPatch) = appVersion.split('.').map { it.toInt() }

android {
    namespace = "com.husarp.ankigo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.husarp.ankigo"
        minSdk = 26
        targetSdk = 35
        versionCode = vMajor * 10000 + vMinor * 100 + vPatch
        versionName = appVersion
    }

    // The release key lives outside the project (never published): its file and passwords are in
    // ~/.keystores/ankigo-signing.properties. Without it the release build is signed with
    // this PC's debug key instead.
    val signing = Properties().apply {
        val f = File(System.getProperty("user.home"), ".keystores/ankigo-signing.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        if (signing.isNotEmpty()) create("release") {
            storeFile = file(signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // FileProvider: hands the downloaded update APK to Android's installer.
    implementation("androidx.core:core-ktx:1.13.1")
    testImplementation("junit:junit:4.13.2")
    // org.json is part of Android but only stubs exist in JVM unit tests; the real library for tests.
    testImplementation("org.json:json:20240303")
}
