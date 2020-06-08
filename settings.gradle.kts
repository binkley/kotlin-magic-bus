rootProject.name = "kotlin-magic-bus"

pluginManagement {
    val detektPluginVersion: String by settings
    val kotlinVersion: String by settings
    val ktlintGradlePlugin: String by settings
    val versionsPluginVersion: String by settings

    plugins {
        kotlin("jvm") version kotlinVersion
        id("io.gitlab.arturbosch.detekt") version detektPluginVersion
        id("org.jlleitschuh.gradle.ktlint") version ktlintGradlePlugin
        id("com.github.ben-manes.versions") version versionsPluginVersion
    }
}
