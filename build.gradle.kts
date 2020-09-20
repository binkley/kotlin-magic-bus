import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.api.tasks.wrapper.Wrapper.DistributionType.ALL
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val assertJVersion: String by project
val gradleWrapperVersion: String by project
val jacocoVersion: String by project
val javaVersion: String by project
val junitVersion: String by project
val kotlinVersion: String by project
val ktlintVersion: String by project
val lombokVersion: String by project
val pitestJUnit5PluginVersion: String by project

plugins {
    `build-dashboard`
    `project-report`
    kotlin("jvm")
    id("org.jetbrains.dokka")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
    id("info.solidsoft.pitest")
    id("com.github.ben-manes.versions")
    jacoco
}

version = "0-SNAPSHOT"
group = "hm.binkley.labs"

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.assertj:assertj-core:$assertJVersion")
}

detekt {
    failFast = true
    // No support yet for configuring directly in Gradle
    config = files("config/detekt.yml")
}

jacoco {
    toolVersion = jacocoVersion
}

ktlint {
    outputColorName.set("RED")
    version.set(ktlintVersion)
}

pitest {
    coverageThreshold.set(82)
    junit5PluginVersion.set(pitestJUnit5PluginVersion)
    mutationThreshold.set(100)
    timestampedReports.set(false)
}

tasks {
    withType<DependencyUpdatesTask> {
        rejectVersionIf {
            !isStable(candidate.version) && isStable(currentVersion)
        }
    }

    withType<KotlinCompile> {
        kotlinOptions {
            allWarningsAsErrors = true
            jvmTarget = javaVersion
            javaParameters = true
            useIR = true
        }
    }

    test {
        useJUnitPlatform()

        finalizedBy(jacocoTestReport)
    }

    jacocoTestCoverageVerification {
        violationRules {
            rule {
                limit {
                    minimum = "1.00".toBigDecimal()
                }
            }
        }
    }

    ktlintCheck {
        dependsOn += ktlintFormat
    }

    check {
        dependsOn += jacocoTestCoverageVerification
        dependsOn += ktlintCheck
        dependsOn += pitest
    }

    withType<Wrapper> {
        gradleVersion = gradleWrapperVersion
        distributionType = ALL
    }
}

val otherReleasePatterns = "^[0-9,.v-]+(-r)?$".toRegex()

fun isStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any {
        version.toUpperCase().contains(it)
    }
    val otherReleasePattern = otherReleasePatterns.matches(version)

    return stableKeyword || otherReleasePattern
}
