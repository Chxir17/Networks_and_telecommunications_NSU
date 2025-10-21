plugins {
    kotlin("jvm") version "1.9.10"
    application
}

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
}
dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.1")
}
application {
    mainClass.set("MainKt")
}
