import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.api.tasks.wrapper.Wrapper.DistributionType.ALL
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val assertJVersion: String by project
val checkstyleVersion: String by project
val gradleWrapperVersion: String by project
val jacocoVersion: String by project
val javaVersion: String by project
val junitVersion: String by project
val kotlinVersion: String by project
val kotlinxCoroutinesVersion: String by project
val ktlintVersion: String by project
val lombokVersion: String by project
val pitestJUnit5PluginVersion: String by project

plugins {
    `build-dashboard`
    `project-report`
    kotlin("jvm")
    id("com.github.ben-manes.versions")
    id("info.solidsoft.pitest")
    id("io.gitlab.arturbosch.detekt")
    // TODO: Reenable after CVE-2021-42550 resolved
    // id("org.jlleitschuh.gradle.ktlint")
    id("org.owasp.dependencycheck")
    jacoco
}

version = "0-SNAPSHOT"
group = "hm.binkley.labs"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.projectlombok:lombok:$lombokVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.assertj:assertj-core:$assertJVersion")
}

configurations.all {
    resolutionStrategy.eachDependency {
        if ("org.jetbrains.kotlin" == requested.group) {
            useVersion(kotlinVersion)
        }
    }
}

detekt {
    // No support yet for configuring directly in Gradle
    config = files("config/detekt.yml")
}

jacoco {
    toolVersion = jacocoVersion
}

/*
ktlint {
    outputColorName = "RED"
    version = ktlintVersion
}
*/

pitest {
    junit5PluginVersion = pitestJUnit5PluginVersion
    mutationThreshold = 86 // TODO: Return to 100%
    timestampedReports = false
}

dependencyCheck {
    failBuildOnCVSS = 0.0f // Kotlin is strict here, no simple "0"
    skip = "owasp.skip".toBoolean() // DEFAULT is false
    // TODO: Something is different from this project and the template
    // suppressionFile = rootProject.file("config/owasp-suppressions.xml")
    suppressionFile = "config/owasp-suppressions.xml"

    /* TODO: Needs syntax/type fixing specific to Kotlin
    nvd {
        apiKey = findProperty("owasp.nvdApiKey") ?: System.getenv("OWASP_NVD_API_KEY")
    }
    */
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
        }
    }

    test {
        useJUnitPlatform()

        testLogging {
            showStandardStreams = true
        }

        finalizedBy(jacocoTestReport)
    }

    jacocoTestCoverageVerification {
        violationRules {
            rule {
                limit {
                    // TODO: JaCoCo vs Kotlin 1.6: this had been 100%
                    minimum = "0.98".toBigDecimal()
                }
            }
        }
    }

    /*
    ktlintCheck {
        dependsOn += ktlintFormat
    }
    */

    check {
        // dependsOn += ktlintCheck
        dependsOn += jacocoTestCoverageVerification
        dependsOn += dependencyCheckAnalyze
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
