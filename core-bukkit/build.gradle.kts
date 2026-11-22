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
    implementation(project(":core"))
    implementation("xyz.xenondevs:particle:1.8.4")

    implementation("org.postgresql:postgresql:42.7.1")
    implementation("com.zaxxer:HikariCP:4.0.3")
    implementation("redis.clients:jedis:5.1.2")
    compileOnly(files("../libs/protocol.jar", "../libs/spigot-1.8.8-craftbukkit.jar"))
}

repositories {
    mavenLocal()  // ← Isso é importante!
    mavenCentral()
}

tasks.register<Copy>("copyJar") {
    dependsOn("jar")

    /* Junta todos os plugins compilados em <raiz>/all-jars */
    from(tasks.jar.flatMap { it.archiveFile })
    into(rootProject.layout.projectDirectory.dir("all-jars"))
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    /* Assinaturas das dependências não valem no jar único (dariam "Invalid signature file digest") */
    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    finalizedBy("copyJar")
}
