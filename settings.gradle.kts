pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // SDK RuStore (design D7): exclusiveContent + includeGroup не даёт
        // стороннему репозиторию стать источником остальных артефактов.
        // Только здесь: FAIL_ON_PROJECT_REPOS не позволяет объявить
        // репозиторий в app/build.gradle.kts.
        exclusiveContent {
            forRepository {
                maven("https://nexus-external.rustore.ru/repository/maven-rustore-exposed")
            }
            filter {
                includeGroup("ru.rustore.sdk")
            }
        }
    }
}

rootProject.name = "Jetpack-MyWorkouts"
include(":app")
include(":screenshot-tests")
