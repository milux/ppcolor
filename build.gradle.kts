buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    application
    kotlin("jvm") version "1.5.10"
//    id "com.github.breadmoirai.github-release" version "2.2.0"
}

group = "de.milux.ppcolor"
version = "3.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("uk.co.caprica:vlcj:4.7.1")
    implementation("org.slf4j:slf4j-api:1.7.25")
    implementation("org.slf4j:slf4j-simple:1.7.25")
    implementation("com.github.kevinstern:software-and-algorithms:1.0")
}

tasks.compileKotlin {
    kotlinOptions.jvmTarget = "1.8"
}
tasks.compileTestKotlin {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.jar {
    archiveBaseName.set("ppcolor")
    manifest {
        attributes(mapOf("Main-Class" to "de.milux.ppcolor.MainKt"))
    }
    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

/*githubRelease {
    token getProperty("github.token").toString()
    owner "milux"
    repo "ppcolor"
    tagName "${version}"
    releaseAssets "build/libs/ppcolor-${version}.jar"
    overwrite true
}*/
