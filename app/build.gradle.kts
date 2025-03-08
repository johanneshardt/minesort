version = "0.3.0"

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
}

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly(libs.paper.api)
    implementation(libs.kyori.plain.serializer)
}

tasks.shadowJar {
    manifest {
        attributes["paperweight-mappings-namespace"] = "mojang"
    }
    archiveFileName.set("${rootProject.name}-${project.version}.jar")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xwhen-guards")
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
