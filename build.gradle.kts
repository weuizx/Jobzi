plugins {
	kotlin("jvm") version "2.2.21"
	kotlin("plugin.spring") version "2.2.21"
	kotlin("plugin.jpa") version "2.2.21"
	id("org.springframework.boot") version "4.0.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "dev.weuizx"
version = "0.0.1-SNAPSHOT"
description = "Telegram bot platform for quick job matching in the informal sector"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
	maven {
		url = uri("https://jitpack.io")
	}
	maven {
		url = uri("https://mvn.mchv.eu/repository/mchv/")
	}
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-websocket")
	implementation("org.jetbrains.kotlin:kotlin-reflect")

	// JSON processing
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("com.fasterxml.jackson.core:jackson-databind")

	// Database
	runtimeOnly("org.postgresql:postgresql")

	// Liquibase
	implementation("org.liquibase:liquibase-core")

	// Telegram Bot API
	implementation("org.telegram:telegrambots-spring-boot-starter:6.9.7.1")

	// Telegram Client API (TDLight)
	// Основная библиотека
	implementation("it.tdlight:tdlight-java:3.4.4+td.1.8.52")

	// Нативные библиотеки (используют свою нумерацию версий!)
	// Для macOS Intel
	runtimeOnly(group = "it.tdlight", name = "tdlight-natives", version = "4.0.558", classifier = "macos_amd64")
	// Для macOS Apple Silicon (ARM)
	runtimeOnly(group = "it.tdlight", name = "tdlight-natives", version = "4.0.558", classifier = "macos_arm64")
	// Раскомментируйте для Linux deployment:
	// runtimeOnly(group = "it.tdlight", name = "tdlight-natives", version = "4.0.558", classifier = "linux_amd64_gnu_ssl3")

	// Coroutines для асинхронных операций
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.9.0")

	// SLF4J для логирования
	implementation("org.slf4j:slf4j-api:2.0.9")

	// Guava for rate limiting
	implementation("com.google.guava:guava:33.0.0-jre")

	// Apache POI for Excel export
	implementation("org.apache.poi:poi:5.2.5")
	implementation("org.apache.poi:poi-ooxml:5.2.5")

	// Testing
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
