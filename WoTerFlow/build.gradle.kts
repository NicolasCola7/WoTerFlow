import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "1.9.20"
    application
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

group = "io.github.dvgniele"
version = "1.0-SNAPSHOT"

val ktor_version = "2.3.5"
val jena_version = "4.8.0"

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
    testImplementation(kotlin("test"))

    // Rest Assured
    // https://mvnrepository.com/artifact/io.rest-assured/rest-assured
    implementation("io.rest-assured:rest-assured:5.3.0")

    // https://mvnrepository.com/artifact/io.rest-assured/json-path
    testImplementation("io.rest-assured:json-path:5.3.0")


    // https://mvnrepository.com/artifact/com.squareup.okhttp3/okhttp
    testImplementation("com.squareup.okhttp3:okhttp:4.11.0")


    // JUnit
    // https://mvnrepository.com/artifact/junit/junit
    //testImplementation("junit:junit:4.13.2")

    // https://mvnrepository.com/artifact/org.junit.jupiter/junit-jupiter-api
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.3")


    // Jena
    // https://mvnrepository.com/artifact/org.apache.jena/jena-core
    implementation("org.apache.jena:jena-core:$jena_version")

    // https://mvnrepository.com/artifact/org.apache.jena/jena-arq
    implementation("org.apache.jena:jena-arq:$jena_version")

    // https://mvnrepository.com/artifact/org.apache.jena/jena-tdb
    implementation("org.apache.jena:jena-tdb:$jena_version")

    // https://mvnrepository.com/artifact/org.apache.jena/jena-shacl
    implementation("org.apache.jena:jena-shacl:$jena_version")

    // https://mvnrepository.com/artifact/org.apache.jena/jena-tdb2
    implementation("org.apache.jena:jena-tdb2:$jena_version")

    // https://mvnrepository.com/artifact/com.github.erosb/everit-json-schema
    //implementation("com.github.erosb:everit-json-schema:1.14.2")

    // https://mvnrepository.com/artifact/org.apache.jena/jena-fuseki
    //implementation("org.apache.jena:jena-fuseki-main:$jena_version")




    // https://mvnrepository.com/artifact/com.apicatalog/titanium-json-ld
    implementation("com.apicatalog:titanium-json-ld:1.3.2")


    // KTOR
    // https://mvnrepository.com/artifact/io.ktor/ktor-server-core
    implementation("io.ktor:ktor-server-core:$ktor_version")

    // https://mvnrepository.com/artifact/io.ktor/ktor-server-cio
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-server-cio:$ktor_version")


    // https://mvnrepository.com/artifact/io.ktor/ktor-server-call-logging
    implementation("io.ktor:ktor-server-call-logging:$ktor_version")

    // https://mvnrepository.com/artifact/io.ktor/ktor-server-content-negotiation
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")

    // https://mvnrepository.com/artifact/io.ktor/ktor-serialization-jackson
    implementation("io.ktor:ktor-serialization-jackson:$ktor_version")



    // JSONPath
    // https://mvnrepository.com/artifact/com.jayway.jsonpath/json-path
    implementation("com.jayway.jsonpath:json-path:2.8.0")

    // XPath
    // https://mvnrepository.com/artifact/net.sf.saxon/Saxon-HE
    implementation("net.sf.saxon:Saxon-HE:12.3")

    // https://mvnrepository.com/artifact/com.fasterxml.jackson.dataformat/jackson-dataformat-xml
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.15.3")


    // https://mvnrepository.com/artifact/ch.qos.logback/logback-classic
    implementation("ch.qos.logback:logback-classic:1.4.11")

}

tasks.test {
    //useJUnitPlatform()
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "17"
    }

    processResources {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    shadowJar {
        archiveFileName.set("woterflow.jar")

        from("src/main/resources"){
            include("**/*.*")
        }
    }
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("MainKt")
}