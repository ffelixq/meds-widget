buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 has built-in Kotlin; this upgrades its embedded compiler to the
        // version used by the stable Compose compiler plugin.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.ktlint) apply false
}

val detektConfiguration = configurations.create("detekt")

dependencies {
    add(detektConfiguration.name, libs.detekt.cli)
}

allprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
}

tasks.register<JavaExec>("detekt") {
    group = "verification"
    description = "Runs the stable Detekt CLI against production Kotlin sources."
    classpath = detektConfiguration
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    args(
        "--input",
        layout.projectDirectory.dir("app/src/main/java").asFile.absolutePath,
        "--config",
        layout.projectDirectory.file("config/detekt/detekt.yml").asFile.absolutePath,
        "--build-upon-default-config",
        "--parallel",
    )
}

tasks.register("formatCheck") {
    group = "verification"
    dependsOn(":app:ktlintCheck")
}

tasks.register("formatApply") {
    group = "formatting"
    dependsOn(":app:ktlintFormat")
}

tasks.register("staticAnalysis") {
    group = "verification"
    dependsOn("detekt", ":app:lintDebug")
}

tasks.register("fullValidation") {
    group = "verification"
    description = "Runs the practical Gradle validation suite."
    dependsOn("formatCheck", "detekt", ":app:lintDebug", ":app:testDebugUnitTest", ":app:assembleDebug")
}
