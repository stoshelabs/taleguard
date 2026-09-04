import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import java.util.jar.JarFile

plugins {
    id("java")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25)) // Matches HytaleServer.jar compiled version (Java 25)
    }
}

group = "dev.stoshe"
// Kept in gradle.properties so a release is one edit, and so two builds cannot
// share a version the way 1.0.0 did while its contents moved on.
version = (findProperty("version") as? String)?.takeIf { it.isNotBlank() && it != "unspecified" } ?: "1.1.0"

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

/**
 * The Hytale release line this build compiles against — "0.6" for a server
 * reporting 0.6.3.
 *
 * <p>It matters more here than for an ordinary plugin: TaleGuard's mixins bind to
 * exact method descriptors in the server, so a jar built against one line has no
 * business being dropped into another. Reading it from the server jar's manifest
 * means the file name cannot disagree with what it was built against.
 *
 * <p>The line, not the patch: one build serves the whole 0.6.x. Returns null when
 * the manifest reports something that is not a version (older builds report a
 * date), and -PhytaleVersion overrides.
 */
fun detectHytaleLine(): String? {
    (findProperty("hytaleVersion") as? String)?.takeIf { it.isNotBlank() }?.let { return it }

    val reported = try {
        JarFile(file(hytaleServerJar)).use { jar ->
            jar.manifest?.mainAttributes?.getValue("Implementation-Version")
        }
    } catch (e: Exception) {
        logger.warn("Could not read the Hytale version from $hytaleServerJar: ${e.message}")
        null
    }

    return reported?.let { Regex("^(\\d+\\.\\d+)").find(it)?.groupValues?.get(1) }
}

val hytaleLine = detectHytaleLine()

if (hytaleLine == null) {
    logger.warn("Hytale version not detected; the jar will be named without one. Pass -PhytaleVersion=0.6 to set it.")
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

/**
 * The server range this build declares it works with.
 *
 * <p>A mixin binds to exact method descriptors, so a build made against one line
 * has no business loading on another: the redirects would silently fail to apply
 * and TaleGuard would report itself healthy while protecting nothing. Declaring
 * the range makes the server refuse the plugin instead, which is the failure we
 * want. Unknown line falls back to "*", the old behaviour.
 */
val hytaleRange = hytaleLine?.let { line ->
    val next = line.substringBefore('.').toInt().toString() + "." +
            (line.substringAfter('.').toInt() + 1)
    ">=$line.0 <$next.0"
} ?: "*"

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("hytaleRange", hytaleRange)

    // The manifest carried a literal "${'$'}{version}" into every release because
    // nothing ever expanded it.
    filesMatching("manifest.json") {
        expand("version" to project.version, "hytaleRange" to hytaleRange)
    }
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveBaseName.set("TaleGuard")
    archiveVersion.set(project.version.toString())

    // TaleGuard-1.0.0-hytale-0.6.jar. Mixins bind to exact server descriptors, so
    // which line a build targets belongs on the file rather than in a changelog.
    hytaleLine?.let { archiveClassifier.set("hytale-$it") }

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
        hytaleLine?.let { attributes("Hytale-Version" to it) }
    }
}
