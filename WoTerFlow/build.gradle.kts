import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

group = "io.github.dvgniele"
version = "0.1.9"

val ktor_version = "3.2.3"
val jena_version = "4.8.0"
val kotlin_version = "2.2.0"

plugins {
    kotlin("jvm") version "2.2.0"
    application
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

repositories {
    mavenCentral()
    maven {
        setUrl("https://packages.confluent.io/maven/")
    }
}

sourceSets {
    main {
        resources.srcDir("src/main/resources")
    }
}
dependencies {
    // Jena
    // https://mvnrepository.com/artifact/org.apache.jena/jena-core
    implementation("org.apache.jena:jena-core:$jena_version")

    // https://mvnrepository.com/artifact/org.apache.jena/jena-shacl
    implementation("org.apache.jena:jena-shacl:$jena_version")

    // https://mvnrepository.com/artifact/org.apache.jena/jena-tdb2
    implementation("org.apache.jena:jena-tdb2:$jena_version")

    // https://mvnrepository.com/artifact/org.apache.jena/jena-fuseki
    //implementation("org.apache.jena:jena-fuseki-main:$jena_version")

    //  JSONLD
    // https://mvnrepository.com/artifact/com.apicatalog/titanium-json-ld
    //implementation("com.apicatalog:titanium-json-ld:1.6.0")

    // modified titanium-json-ld version
    implementation(files("libs/titanium-json-ld-1.4.1-dr-snapshot.jar"))

    // KTOR
    // https://mvnrepository.com/artifact/io.ktor/ktor-server-core
    implementation("io.ktor:ktor-server-core:$ktor_version")

    // https://mvnrepository.com/artifact/io.ktor/ktor-server-cio
    implementation("io.ktor:ktor-server-cio:$ktor_version")

    // https://mvnrepository.com/artifact/io.ktor/ktor-server-call-logging
    implementation("io.ktor:ktor-server-call-logging:$ktor_version")

    // https://mvnrepository.com/artifact/io.ktor/ktor-server-content-negotiation
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")

    // https://mvnrepository.com/artifact/io.ktor/ktor-serialization-jackson
    implementation("io.ktor:ktor-serialization-jackson:$ktor_version")

    // JSONPath
    // https://mvnrepository.com/artifact/com.jayway.jsonpath/json-path
    implementation("com.jayway.jsonpath:json-path:2.9.0")

    // XPath
    // https://mvnrepository.com/artifact/net.sf.saxon/Saxon-HE
    implementation("net.sf.saxon:Saxon-HE:12.8")

    // https://mvnrepository.com/artifact/com.fasterxml.jackson.dataformat/jackson-dataformat-xml
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.20.0")

    // https://mvnrepository.com/artifact/ch.qos.logback/logback-classic
    implementation("ch.qos.logback:logback-classic:1.5.18")

    // Guava
    implementation("com.google.guava:guava:32.1.3-jre")

    //  TESTING
    testImplementation(kotlin("test"))

    testImplementation("io.ktor:ktor-server-test-host:${ktor_version}")

    // Rest Assured
    // https://mvnrepository.com/artifact/io.rest-assured/rest-assured
    testImplementation("io.rest-assured:rest-assured:5.5.6")

    // https://mvnrepository.com/artifact/io.rest-assured/json-path
    testImplementation("io.rest-assured:json-path:5.5.6")

    // https://mvnrepository.com/artifact/com.squareup.okhttp3/okhttp
    testImplementation("com.squareup.okhttp3:okhttp:5.1.0")

    // JUnit
    // https://mvnrepository.com/artifact/org.junit.jupiter/junit-jupiter-api
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.3")

    implementation("io.ktor:ktor-client-core:${ktor_version}")
}

    tasks {
        processResources {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }

        shadowJar {
            archiveFileName.set("woterflow-${project.version}.jar")

            from("src/main/resources") {
                include("**/*.*")
            }
        }

        distZip {
            dependsOn(shadowJar)
        }
        distTar {
            dependsOn(shadowJar)
        }
        startScripts {
            dependsOn(shadowJar)
        }

        startShadowScripts {
            dependsOn(jar)
        }

        test {
            useJUnitPlatform()
        }
    }

    kotlin {
        compilerOptions {
            optIn.add("kotlin.RequiresOptIn")
        }
        jvmToolchain(20)
    }

    application {
        mainClass.set("MainKt")
    }
