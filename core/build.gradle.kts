import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("java")
}

/* O jar do SkyWars roda no servidor 1.8.8 (Java 8). */
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

dependencies {
    implementation("redis.clients:jedis:5.1.2")
    implementation("org.postgresql:postgresql:42.7.1")
    implementation("com.zaxxer:HikariCP:4.0.3")

    compileOnly(files("../libs/zartema-api.jar", "../libs/slime.jar", "../libs/spigot-1.8.8-craftbukkit.jar"))
    compileOnly("com.mojang:authlib:1.5.21")
}

repositories {
    mavenLocal()  // ← Isso é importante!
    mavenCentral()
    maven(url = "https://libraries.minecraft.net") // ← IMPORTANTE para authlib
}