import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

// Секреты подписи (план flavors-implementation.md): .secrets/secrets.properties
// загружается make-целью _load_secrets из приватного репозитория
// android-secrets. Дефолты в signingConfigs — чтобы конфигурационная фаза
// не падала на машине без секретов (риск 2): упадёт только release-сборка.
val secretsProperties = Properties()
val secretsPropertiesFile = rootProject.file(".secrets/secrets.properties")
if (secretsPropertiesFile.exists()) {
    secretsPropertiesFile.inputStream().use { secretsProperties.load(it) }
}

android {
    namespace = "com.myworkouts"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.myworkouts"
        minSdk = 26
        targetSdk = 37
        versionCode = project.findProperty("VERSION_CODE")?.toString()?.toInt() ?: 1
        versionName = project.findProperty("VERSION_NAME")?.toString() ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Флейворы дистрибуции (план flavors-implementation.md): без суффиксов
    // applicationId/versionName — один package для всех каналов, как в
    // JetpackDays. RUSTORE_FEATURES — компайл-тайм-гейт для кнопок
    // «Оценить приложение»/«Поделиться приложением» (more-screen).
    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            buildConfigField("Boolean", "RUSTORE_FEATURES", "false")
        }
        create("rustore") {
            buildConfigField("Boolean", "RUSTORE_FEATURES", "true")
        }
    }

    signingConfigs {
        create("release") {
            val keystoreFile =
                secretsProperties["KEYSTORE_FILE"] as? String
                    ?: ".secrets/keystore/myworkouts-release.keystore"
            val keystorePassword = secretsProperties["KEYSTORE_PASSWORD"] as? String ?: ""
            val keyAlias = secretsProperties["KEY_ALIAS"] as? String ?: "upload"
            val keyPassword = secretsProperties["KEY_PASSWORD"] as? String ?: ""

            storeFile = rootProject.file(keystoreFile)
            storePassword = keystorePassword
            this.keyAlias = keyAlias
            this.keyPassword = keyPassword
        }
    }

    buildTypes {
        debug {
            // Телеметрия выключена в debug (design D5): обе meta-data в
            // манифесте читают один placeholder.
            manifestPlaceholders["crashlyticsCollectionEnabled"] = false
        }
        release {
            // R8: вырезает неиспользуемый код (иначе material-icons-extended
            // добавляет ~4 МБ неиспользуемых иконок в APK).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            manifestPlaceholders["crashlyticsCollectionEnabled"] = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    // Эталонная реализация org.json для JVM unit-тестов: в android.jar это
    // заглушки, бросающие "not mocked" (app-data-screen, design Decision 1).
    testImplementation(libs.org.json)
    testRuntimeOnly(libs.junit.platform.launcher)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

detekt {
    config.setFrom(files("../config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
}

// ponytail: schema export в app/schemas — экспортированная JSON-схема v1 нужна
// для будущих миграций (workouts в v2). Стоит ноль усилий сейчас, без неё
// миграция v1→v2 не тестопригодна (Room MigrationTestHelper требует json).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // ponytail: при пустом test source set (сейчас тестов нет после удаления RootScreenViewModelTest)
    // не валить сборку — allowOnNoTests/failOnNoDiscoveredTests=false.
    failOnNoDiscoveredTests = false
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
