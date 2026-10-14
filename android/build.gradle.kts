buildscript {
    dependencies {
        // Screenshot tests only (renders the Views off-device for design checks; test scope, never in the APK).
        // Its Kotlin 2.3 BOM is left out so the app keeps building with the Kotlin plugin below.
        classpath("app.cash.paparazzi:paparazzi-gradle-plugin:2.0.0-alpha04") { exclude(group = "org.jetbrains.kotlin") }
    }
}

plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
}
