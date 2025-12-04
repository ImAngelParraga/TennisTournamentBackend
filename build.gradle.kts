val kotlin_version: String by project
val logback_version: String by project
val exposedVersion = "0.58.0"
val koinVersion = "4.1.0-Beta5"
val ktor_version: String by project
val koinAnnotationsVersion = "2.0.0-Beta1"
val postgresqlDriverVersion = "42.7.5"

plugins {
    kotlin("jvm") version "2.0.20"
    id("io.ktor.plugin") version "3.0.3"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20"
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
    implementation("io.ktor:ktor-server-cors-jvm")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    //implementation("ch.qos.logback:logback-classic:$logback_version")

    implementation("com.github.ImAngelParraga:TennisTournamentLib:v0.0.2")
    //implementation(files("C:\\Users\\ranki\\IdeaProjects\\TennisTournamentLib\\build\\libs\\TennisTournamentLib-0.0.2.jar"))

    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-json:$exposedVersion")
    implementation("org.postgresql:postgresql:$postgresqlDriverVersion")

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
    arg("KOIN_CONFIG_CHECK","true")
}
