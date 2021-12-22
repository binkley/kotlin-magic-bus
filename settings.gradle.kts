rootProject.name = "kotlin-magic-bus"

pluginManagement {
    val dependencyCheckPluginVersion: String by settings
    val detektPluginVersion: String by settings
    val dokkaPluginVersion: String by settings
    val kotlinVersion: String by settings
    val ktlintPluginVersion: String by settings
    val pitestPluginVersion: String by settings
    val versionsPluginVersion: String by settings

    plugins {
        kotlin("jvm") version kotlinVersion
        id("com.github.ben-manes.versions") version versionsPluginVersion
        id("info.solidsoft.pitest") version pitestPluginVersion
        id("io.gitlab.arturbosch.detekt") version detektPluginVersion
        id("org.jetbrains.dokka") version dokkaPluginVersion
        id("org.jlleitschuh.gradle.ktlint") version ktlintPluginVersion
        id("org.owasp.dependencycheck") version dependencyCheckPluginVersion
    }
}
