import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

plugins {
    id("java")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25)) // Matches HytaleServer.jar compiled version (Java 25)
    }
}

group = "dev.stoshe"
version = "1.0.0"

val hytaleHome: String by extra {
    if (project.hasProperty("hytale_home")) {
        project.findProperty("hytale_home") as String
    } else {
        val os = DefaultNativePlatform.getCurrentOperatingSystem()
        when {
            os.isWindows -> "${System.getProperty("user.home")}/AppData/Roaming/Hytale"
            os.isMacOsX -> "${System.getProperty("user.home")}/Library/Application Support/Hytale"
            os.isLinux -> {
                val flatpakPath = "${System.getProperty("user.home")}/.var/app/com.hypixel.HytaleLauncher/data/Hytale"
                if (file(flatpakPath).exists()) {
                    flatpakPath
                } else {
                    "${System.getProperty("user.home")}/.local/share/Hytale"
                }
            }

            else -> throw GradleException(
                "Could not detect Hytale install. Set -Phytale_home=/path/to/Hytale."
            )
        }
    }
}

val patchline = (findProperty("patchline") as? String) ?: "release"
val hytaleServerJar = "$hytaleHome/install/$patchline/package/game/latest/Server/HytaleServer.jar"

if (!file(hytaleServerJar).exists()) {
    throw GradleException("HytaleServer.jar not found at: $hytaleServerJar")
}

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/repository/maven-public/")
    maven("https://maven.fabricmc.net/")
}

dependencies {
    compileOnly(files(hytaleServerJar))
    
    // Mixin boot dependencies packaged in earlybridge fat jar
    implementation("org.ow2.asm:asm:9.7")
    implementation("org.ow2.asm:asm-analysis:9.7")
    implementation("org.ow2.asm:asm-commons:9.7")
    implementation("org.ow2.asm:asm-tree:9.7")
    implementation("org.ow2.asm:asm-util:9.7")
    implementation("net.fabricmc:sponge-mixin:0.16.5+mixin.0.8.7")
    implementation("org.ow2.sat4j:org.ow2.sat4j.core:2.3.6")
    implementation("org.ow2.sat4j:org.ow2.sat4j.pb:2.3.6")
    implementation("com.google.guava:guava:33.2.1-jre")
    implementation("com.google.code.gson:gson:2.10.1")
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveBaseName.set("TaleGuard")
    archiveVersion.set(project.version.toString())

    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })

    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")

    manifest {
        attributes(
            "Implementation-Title" to "TaleGuard",
            "Implementation-Version" to project.version
        )
    }
}
