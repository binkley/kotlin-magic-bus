rootProject.name = "kotlin-magic-bus"

pluginManagement {
    val detektPluginVersion: String by settings
    val dokkaPluginVersion: String by settings
    val kotlinVersion: String by settings
    val ktlintPluginVersion: String by settings
    val pitestPluginVersion: String by settings
    val versionsPluginVersion: String by settings

    plugins {
        kotlin("jvm") version kotlinVersion
        id("org.jetbrains.dokka") version dokkaPluginVersion
        id("io.gitlab.arturbosch.detekt") version detektPluginVersion
        id("org.jlleitschuh.gradle.ktlint") version ktlintPluginVersion
        id("info.solidsoft.pitest") version pitestPluginVersion
        id("com.github.ben-manes.versions") version versionsPluginVersion
    }
}
