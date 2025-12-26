plugins {
    kotlin("jvm") version "1.9.20"
    id("com.google.protobuf") version "0.9.5"
    application
}

group = "com"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Protobuf Kotlin
    implementation("com.google.protobuf:protobuf-kotlin:4.33.0")
    api("com.google.protobuf:protobuf-kotlin:4.33.0")

    implementation("com.google.protobuf:protobuf-java:4.33.0")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3")

    // Ktor Network
    implementation("io.ktor:ktor-network-jvm:2.3.5")

    // Kotlin standard library
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")

    // Arrow
    implementation("io.arrow-kt:arrow-core:1.2.0")
    implementation("io.arrow-kt:arrow-fx-coroutines:1.2.0")

    // Test
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}




application {
    mainClass.set("onlinesnake.OnlineSnakeAppKt")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.33.0"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("kotlin")
            }
        }
    }
}


kotlin {
    sourceSets.main {
        // Пути к сгенерированным Kotlin-классам
        kotlin.srcDir("build/generated/source/proto/main/kotlin")
    }
}



tasks.jar {
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    from({
        configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
    })
}
