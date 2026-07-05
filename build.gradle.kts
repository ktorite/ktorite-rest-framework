
plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    id("java-library")
    id("maven-publish")
}

group = "org.ktorite"
version = "1.0.1"

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://jitpack.io")
}

kotlin {
    jvmToolchain(17)
}

publishing {
    publications {
        create<MavenPublication>("rest") {
            from(components["java"])
            groupId = "com.github.ktorite"
            artifactId = "ktorite-rest-framework"
            version = "v1.0.1"
        }
    }
}

dependencies {
    api("com.github.ktorite:ktorite-core:1.0.1")
}
