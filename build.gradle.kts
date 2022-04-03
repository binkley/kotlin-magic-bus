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
    id("org.jlleitschuh.gradle.ktlint")
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

    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.assertj:assertj-core:$assertJVersion")

    // TODO: Workaround CVE(s)
    detekt("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion") {
        because("https://nvd.nist.gov/vuln/detail/CVE-2022-24329")
    }
}

detekt {
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
    junit5PluginVersion.set(pitestJUnit5PluginVersion)
    mutationThreshold.set(100)
    timestampedReports.set(false)
}

dependencyCheck {
    failBuildOnCVSS = 0f
    // TODO: provide "skip" from -D command line
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

    ktlintCheck {
        dependsOn += ktlintFormat
    }

    check {
        dependsOn += ktlintCheck
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
