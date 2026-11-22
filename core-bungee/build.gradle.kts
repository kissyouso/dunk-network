plugins {
    id("java")
}

dependencies {
    implementation(project(":core"))
    implementation("org.javacord:javacord:3.8.0")
    
    implementation("org.postgresql:postgresql:42.7.1")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("redis.clients:jedis:5.1.2")
    compileOnly(files("../libs/zartema-api.jar", "../libs/panda-server.jar"))
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
