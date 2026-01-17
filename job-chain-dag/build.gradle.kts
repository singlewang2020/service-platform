tasks.test {
    useJUnitPlatform()
}

plugins {
    `java-library`
    application
}

group = "com.ruler.one"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17)) // 按你项目实际改：11/17/21
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.postgresql:postgresql:42.7.2")
    implementation("org.springframework:spring-jdbc:5.3.32")
    implementation("org.springframework:spring-context:5.3.32") // 新增依赖，解决注解缺失
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

// 强制 Java 编译使用 UTF-8 编码，解决中文注释乱码
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

application {
    mainClass.set("com.ruler.one.demo.DemoRunner")
}
