import java.util.*

val kotlin_version: String by project
val logback_version: String by project
val exposedVersion = "0.58.0"
val koinVersion = "4.1.0-Beta5"
val ktor_version: String by project
val koinAnnotationsVersion = "2.0.0-Beta1"
val postgresqlDriverVersion = "42.7.7"
val flywayVersion = "12.4.0"

fun firstNonBlank(vararg candidates: String?): String? =
    candidates.firstOrNull { !it.isNullOrBlank() }

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}

buildscript {
    dependencies {
        classpath("org.flywaydb:flyway-database-postgresql:12.0.3")
    }
}

plugins {
    kotlin("jvm") version "2.0.20"
    id("io.ktor.plugin") version "3.0.3"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20"
    id("org.flywaydb.flyway") version "12.0.3"
    application

    id("com.google.devtools.ksp") version "2.0.20-1.0.25"
}

kotlin {
    jvmToolchain(21)
}

group = "bros.parraga"
version = "0.0.1"

application {
    mainClass.set("bros.parraga.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-auth-jvm")
    implementation("io.ktor:ktor-server-auth-jwt-jvm")
    implementation("io.ktor:ktor-server-cors-jvm")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("com.auth0:jwks-rsa:0.22.1")
    //implementation("ch.qos.logback:logback-classic:$logback_version")

    implementation("com.github.ImAngelParraga:TennisTournamentLib:v0.0.2")
    //implementation(files("C:\\Users\\ranki\\IdeaProjects\\TennisTournamentLib\\build\\libs\\TennisTournamentLib-0.0.2.jar"))

    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-json:$exposedVersion")
    implementation("org.postgresql:postgresql:$postgresqlDriverVersion")
    implementation("com.h2database:h2:2.3.232")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")

    implementation("io.insert-koin:koin-ktor3:$koinVersion")
    implementation("io.insert-koin:koin-logger-slf4j:$koinVersion")
    implementation("io.insert-koin:koin-annotations:$koinAnnotationsVersion")
    ksp("io.insert-koin:koin-ksp-compiler:$koinAnnotationsVersion")

    testImplementation("io.ktor:ktor-server-test-host:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlin_version")
    testImplementation("com.h2database:h2:2.3.232")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    //testImplementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    testImplementation("io.insert-koin:koin-test:$koinVersion")
}

ksp {
    arg("KOIN_CONFIG_CHECK", "true")
}

flyway {
    configurations = arrayOf("compileClasspath", "runtimeClasspath")

    val flywayUrl = firstNonBlank(
        providers.gradleProperty("databaseUrl").orNull,
        localProperties.getProperty("databaseUrl"),
        System.getenv("DATABASE_URL")
    )
    val flywayUser = firstNonBlank(
        providers.gradleProperty("databaseUser").orNull,
        localProperties.getProperty("databaseUser"),
        System.getenv("DATABASE_USER")
    )
    val flywayPassword = firstNonBlank(
        providers.gradleProperty("databasePassword").orNull,
        localProperties.getProperty("databasePassword"),
        System.getenv("DATABASE_PASSWORD")
    )

    if (!flywayUrl.isNullOrBlank()) {
        url = flywayUrl
    }
    if (!flywayUser.isNullOrBlank()) {
        user = flywayUser
    }
    if (!flywayPassword.isNullOrBlank()) {
        password = flywayPassword
    }

    locations = arrayOf("filesystem:${projectDir}/src/main/resources/db/migration")
    validateMigrationNaming = true
    outOfOrder = false
    baselineOnMigrate = false
    cleanDisabled = true

    val runningFlywayTask = gradle.startParameter.taskNames.any { it.contains("flyway", ignoreCase = true) }
    if (runningFlywayTask && (flywayUrl.isNullOrBlank() || flywayUser.isNullOrBlank() || flywayPassword.isNullOrBlank())) {
        logger.lifecycle(
            "Flyway DB config is incomplete. Set DATABASE_URL/DATABASE_USER/DATABASE_PASSWORD " +
                    "or Gradle properties databaseUrl/databaseUser/databasePassword."
        )
    }
}
