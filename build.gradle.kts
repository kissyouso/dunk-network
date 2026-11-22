subprojects {
    apply(plugin = "java")

    group = "com.minecraft"
    version = "1.0.0-SNAPSHOT"

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        /* Saída compatível com os servidores (major 61 / Java 17).
         * core, core-bukkit e skywars são compilados com o toolchain Java 8,
         * pois o jar do SkyWars roda no servidor Spigot 1.8.8. */
        if (project.name !in setOf("core", "core-bukkit", "skywars")) {
            options.release.set(17)
        }
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        val lombok = "org.projectlombok:lombok:1.18.48"

        "implementation"(lombok)
        "annotationProcessor"(lombok)
    }
}
