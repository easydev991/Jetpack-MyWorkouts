// Бейджи версий в README.md между маркерами BEGIN_VERSIONS/END_VERSIONS
// держатся актуальными этой задачей (запуск: make update_readme_versions).
// Подключается из корневого build.gradle.kts: apply(from = ...).
tasks.register("updateReadmeVersions") {
    group = "documentation"
    description = "Updates version badges in README.md"

    doLast {
        val readmeFile = File(rootDir, "README.md")
        val versionsTomlFile = File(rootDir, "gradle/libs.versions.toml")
        val gradleWrapperFile = File(rootDir, "gradle/wrapper/gradle-wrapper.properties")
        val appBuildFile = File(rootDir, "app/build.gradle.kts")

        fun String.findAfter(
            prefix: String,
            suffix: String
        ): String? {
            val startIndex = indexOf(prefix)
            if (startIndex == -1) return null
            val valueStart = startIndex + prefix.length
            val endIndex = indexOf(suffix, valueStart)
            if (endIndex == -1) return null
            return substring(valueStart, endIndex)
        }

        val toml = versionsTomlFile.readText()
        val kotlinVersion =
            toml.findAfter("kotlin = \"", "\"")
                ?: throw GradleException("Kotlin version not found in libs.versions.toml")

        val agpVersion =
            toml.findAfter("agp = \"", "\"")
                ?: throw GradleException("AGP version not found in libs.versions.toml")

        val gradleVersion =
            gradleWrapperFile
                .readText()
                .findAfter("gradle-", "-bin.zip")
                ?: throw GradleException("Gradle version not found in gradle-wrapper.properties")

        // AGP 9: compileSdk задаётся блоком `compileSdk { version = release(N) }`.
        val appBuild = appBuildFile.readText()
        val compileSdk =
            appBuild.findAfter("version = release(", ")")
                ?.trim()
                ?: throw GradleException("compileSdk not found in app/build.gradle.kts")

        val minSdk =
            appBuild.findAfter("minSdk = ", "\n")
                ?.trim()
                ?: throw GradleException("minSdk not found in app/build.gradle.kts")

        val newBadges =
            """
            [<img alt="Kotlin Version" src="https://img.shields.io/badge/Kotlin_Version-$kotlinVersion-purple">](https://kotlinlang.org/)
            [<img alt="Android SDK" src="https://img.shields.io/badge/Android_SDK-$compileSdk-green">](https://developer.android.com/)
            [<img alt="Min SDK" src="https://img.shields.io/badge/Min_SDK-$minSdk-informational">](https://developer.android.com/)
            [<img alt="Gradle" src="https://img.shields.io/badge/Gradle-$gradleVersion-blue">](https://gradle.org/)
            [<img alt="AGP" src="https://img.shields.io/badge/AGP-$agpVersion-green">](https://developer.android.com/tools/releases/gradle-plugin)
            """.trimIndent()

        val beginMarker = "<!-- BEGIN_VERSIONS -->"
        val endMarker = "<!-- END_VERSIONS -->"

        val readmeContent = readmeFile.readText()
        val beginIndex = readmeContent.indexOf(beginMarker)
        val endIndex = readmeContent.indexOf(endMarker)

        if (beginIndex == -1 || endIndex == -1) {
            throw GradleException("Version markers not found in README.md")
        }

        readmeFile.writeText(
            readmeContent.substring(0, beginIndex + beginMarker.length) +
                "\n" + newBadges + "\n" +
                readmeContent.substring(endIndex)
        )
        logger.lifecycle("✓ README.md versions updated successfully")
    }
}
