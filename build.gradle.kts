import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.api.tasks.wrapper.Wrapper.DistributionType.ALL
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val gradleWrapperVersion: String by project
val jacocoVersion: String by project
val kotlinVersion: String by project
val kotlinTestVersion: String by project
val lombokVersion: String by project
val mockkVersion: String by project

plugins {
    kotlin("jvm")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
    id("com.github.ben-manes.versions")
    application
    jacoco
}

version = "0-SNAPSHOT"
group = "hm.binkley.labs"

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    // For selectively suppressing code coverage
    compileOnly("org.projectlombok:lombok:$lombokVersion")

    implementation(kotlin("reflect"))
    implementation(kotlin("stdlib-jdk8"))

    testImplementation("io.kotlintest:kotlintest-runner-junit5:$kotlinTestVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
}

application {
    mainClassName = "hm.binkley.labs.Application"
}

detekt {
    failFast = true
    // No support yet for configuring direcly in Gradle
    config = files("config/detekt.yml")
}

jacoco {
    toolVersion = jacocoVersion
}

ktlint {
    outputColorName.set("RED")
}

tasks {
    withType<DependencyUpdatesTask> {
        rejectVersionIf {
            isNonStable(candidate.version) && !isNonStable(currentVersion)
        }
    }

    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "11"
            javaParameters = true
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
                    minimum = BigDecimal.ONE // TODO: Real coverage
                }
            }
        }
    }

    check {
        dependsOn(jacocoTestCoverageVerification)
        // TODO: Do not run both ktlintCheck and ktlintFormat
        dependsOn(ktlintFormat)
    }

    named<JavaExec>("run") {
        jvmArgs(
            "-noverify",
            "-XX:TieredStopAtLevel=1",
            "-Dcom.sun.management.jmxremote"
        )
    }

    withType<Wrapper> {
        gradleVersion = gradleWrapperVersion
        distributionType = ALL
    }
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any {
        version.toUpperCase().contains(it)
    }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}
