plugins {
    id("java")
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    // Jars do servidor ficam em ../libs; nada e baixado da internet.
    compileOnly(fileTree(rootProject.file("libs")) { include("*.jar") })
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(8)
    options.compilerArgs.add("-proc:none")
}

tasks.jar {
    archiveBaseName.set("DunkACBridge")
}
