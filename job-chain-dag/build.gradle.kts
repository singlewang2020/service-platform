plugins {
    `java-library`
    application
    id("org.springframework.boot") version "3.3.7"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.ruler.one"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17)) // 按你项目实际改：11/17/21
    }
}

repositories {
    // 1. local
    mavenLocal()

    // 2. central
    mavenCentral()

    // 3. ali cloud
    maven {
        url = uri("https://maven.aliyun.com/repository/central")
        content { excludeGroup("software.amazon.awssdk") }
    }
    maven {
        url = uri("https://maven.aliyun.com/repository/public")
        content { excludeGroup("software.amazon.awssdk") }
    }
    maven {
        url = uri("https://maven.aliyun.com/repository/spring")
        content { excludeGroup("software.amazon.awssdk") }
    }
    maven {
        url = uri("https://maven.aliyun.com/repository/google")
        content { excludeGroup("software.amazon.awssdk") }
    }

    // 4. RedHat、Gradle
    maven { url = uri("https://maven.repository.redhat.com/ga") }
    maven { url = uri("https://plugins.gradle.org/m2") }
}

dependencies {
    // Web + Actuator
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Validation (Jakarta Validation)
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // JDBC + PostgreSQL
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.postgresql:postgresql:42.7.2")

    // OpenAPI (Swagger UI)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    // Tests
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("com.h2database:h2:2.2.224")
}

// 强制 Java 编译使用 UTF-8 编码，解决中文注释乱码
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

application {
    // 使用 Spring Boot 启动类
    mainClass.set("com.ruler.one.JobChainDagApplication")
}

tasks.test {
    useJUnitPlatform()
}
