plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
}

spotless {
    lineEndings = com.diffplug.spotless.LineEnding.UNIX
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}

subprojects {
    apply(plugin = "com.diffplug.spotless")
    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        lineEndings = com.diffplug.spotless.LineEnding.UNIX
        kotlin {
            target("**/*.kt")
            targetExclude("**/build/**")
            ktlint().editorConfigOverride(
                mapOf(
                    "ktlint_standard_function-naming" to "disabled",
                    "ktlint_standard_backing-property-naming" to "disabled",
                    "ktlint_standard_max-line-length" to "disabled",
                    "ktlint_standard_value-parameter-comment" to "disabled",
                    "ktlint_standard_property-naming" to "disabled"
                )
            )
        }
        kotlinGradle {
            target("**/*.gradle.kts")
            targetExclude("**/build/**")
            ktlint()
        }
    }
}

tasks.register("installGitHooks") {
    group = "help"
    description = "Configures Git to use version-controlled .githooks directory."
    doLast {
        val process = ProcessBuilder("git", "config", "core.hooksPath", ".githooks").start()
        val exitCode = process.waitFor()
        if (exitCode == 0) {
            println("Successfully configured git core.hooksPath to .githooks")
        } else {
            System.err.println("Failed to configure git core.hooksPath")
        }
    }
}
