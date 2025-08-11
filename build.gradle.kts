plugins {
    java
    jacoco
    id("io.qameta.allure") version "2.12.0"
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(19))
    }
}

dependencies {
    // JUnit 5 BOM to align versions (stable 5.13.x line)
    testImplementation(platform("org.junit:junit-bom:5.13.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Allure BOM + JUnit5 adapter
    testImplementation(platform("io.qameta.allure:allure-bom:2.29.1"))
    testImplementation("io.qameta.allure:allure-junit5")

    // Jackson for reading JSON test data
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    testImplementation("com.fasterxml.jackson.core:jackson-annotations:2.17.2")
    testImplementation("com.fasterxml.jackson.core:jackson-core:2.17.2")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Đưa Allure results vào thư mục build/allure-results (API mới)
    systemProperty(
            "allure.results.directory",
            layout.buildDirectory.dir("allure-results").get().asFile.absolutePath
    )
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}

// JaCoCo setup with thresholds
jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        csv.required.set(false)
        html.required.set(true)
    }
    classDirectories.setFrom(
            files(classDirectories.files.map {
                fileTree(it) {
                    // Example excludes (adjust as needed)
                    exclude("**/generated/**")
                }
            })
    )
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    executionData.setFrom(fileTree(buildDir).include("jacoco/test.exec", "jacoco/test/*.exec"))
    sourceDirectories.setFrom(files("src/main/java"))
    classDirectories.setFrom(files("build/classes/java/main"))
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
    dependsOn(tasks.test)
}

// Make 'check' run coverage verification and report
tasks.check {
    dependsOn("jacocoTestCoverageVerification")
    dependsOn("jacocoTestReport")
}

// Allure: generate HTML report to build/reports/allure-report
allure {
    report {
        version.set("2.29.0")
        // By default, it will read from build/allure-results
    }
    adapter {
        frameworks {
            junit5 {
                // use default adapter version managed by allure-bom
            }
        }
        autoconfigureListeners.set(true)
    }
}
