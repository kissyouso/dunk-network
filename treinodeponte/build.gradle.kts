plugins {
    id("java")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":core-bukkit"))

    // PostgreSQL e Redis (mesmo padrão dos demais módulos)
    implementation("org.postgresql:postgresql:42.7.1")
    implementation("com.zaxxer:HikariCP:4.0.3")
    implementation("redis.clients:jedis:5.1.2")
    implementation("org.json:json:20240303")

    compileOnly(files(
        "../libs/slime.jar",
        "../libs/zartema-api.jar",
        "../libs/protocol.jar",
        "../libs/spigot-1.8.8-craftbukkit.jar"
    ))

    testImplementation("junit:junit:4.13.2")
    testImplementation(files("../libs/spigot-1.8.8-craftbukkit.jar"))
}

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://libraries.minecraft.net")
    maven(url = "https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(17)
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
