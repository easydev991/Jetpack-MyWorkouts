// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // com.android.test для :screenshot-tests — объявлен здесь, иначе
    // резолв в модуле падает («plugin is already on the classpath with
    // an unknown version»).
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.detekt) apply false
}

// Скрипт-плагин: задача updateReadmeVersions (бейджи версий в README.md)
// вынесена в gradle/updateReadmeVersions.gradle.kts.
apply(from = "gradle/updateReadmeVersions.gradle.kts")