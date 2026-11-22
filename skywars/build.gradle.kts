import org.gradle.api.tasks.Copy
import org.gradle.jvm.tasks.Jar

import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("java")
}

/* Servidor do SkyWars: Spigot 1.8.8 com Java 8. */
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

fun org.gradle.api.Project.optionalDeployDir(gradleProp: String, envVar: String): String {
    System.getenv(envVar)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    return findProperty(gradleProp)?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: ""
}

val skywarsPluginsDir = optionalDeployDir("skywars.plugins.dir", "SKYWARS_PLUGINS_DIR")

dependencies {
    implementation(project(":core"))
    implementation(project(":core-bukkit"))

    // PostgreSQL e HikariCP (necess�rio para runtime)
    implementation("org.postgresql:postgresql:42.7.1")
    implementation("com.zaxxer:HikariCP:4.0.3")

    compileOnly(files("../libs/protocol.jar", "../libs/spigot-1.8.8-craftbukkit.jar"))

}

repositories {
    mavenLocal()  // ← Isso é importante!
    mavenCentral()
}

tasks.register<Copy>("copyJar") {
    group = "deployment"
    description = "Copia SkyWars (skywars.plugins.dir ou SKYWARS_PLUGINS_DIR)."
    dependsOn(tasks.jar)
    onlyIf { skywarsPluginsDir.isNotEmpty() }
    from(tasks.named<Jar>("jar").flatMap { it.archiveFile })
    into(skywarsPluginsDir)
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

