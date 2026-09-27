plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.myworkouts.screenshots"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Пиннит потребляемый variant :app (github — дефолтный флейвор
        // проекта; распределение на скриншоты не влияет). Без явной
        // стратегии тест-модуль собрал бы 4 test-APK вместо 2.
        missingDimensionStrategy("distribution", "github")
    }

    targetProjectPath = ":app"

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":app"))
    implementation(libs.androidx.junit)
    // AndroidJUnitRunner: не приходит транзитивно от androidx-junit /
    // ui-test-junit4 (проверено: в debugCompileClasspath только core+monitor).
    implementation(libs.androidx.test.runner)
    // UiDevice для takeScreenshot() (почему — план §Capture). Не приходит транзитивно.
    implementation(libs.androidx.uiautomator)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui.test.junit4)
}
