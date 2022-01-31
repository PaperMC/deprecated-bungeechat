plugins {
    id("net.kyori.indra") version "2.0.6"
    id("net.kyori.indra.publishing") version "2.0.6"
}

group = "net.md-5"
val bungeeVersion = "1.16-R0.4"
version = if (bungeeVersion.endsWith("-SNAPSHOT")) {
    "${bungeeVersion.replace("-SNAPSHOT", "")}-deprecated-SNAPSHOT"
} else {
    "$bungeeVersion-deprecated"
}

indra {
    javaVersions().target(8)
    publishAllTo(
        "paper",
        if (bungeeVersion.endsWith("-SNAPSHOT")) {
            "https://papermc.io/repo/repository/maven-snapshots/"
        } else {
            "https://papermc.io/repo/repository/maven-releases/"
        }
    )
}

repositories {
    mavenCentral()
}

val bungee: Configuration by configurations.creating
val bungeeJar: Configuration by configurations.creating {
    extendsFrom(bungee)
    isTransitive = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
    }
}
val bungeeSourcesJar: Configuration by configurations.creating {
    isTransitive = false
}

dependencies {
    bungee("net.md-5:bungeecord-chat:$bungeeVersion")
    bungeeSourcesJar("net.md-5:bungeecord-chat:$bungeeVersion:sources")

    api("com.google.code.gson:gson:2.8.8")
    api("com.google.guava:guava:31.0.1-jre")
}

tasks {
    // deprecator.jar is jpenilla/jar-deprecate fat jar
    val deprecateJar = register("deprecateJar", DeprecateJar::class) {
        deprecator.set(layout.projectDirectory.file("deprecator.jar"))
        input.set(layout.file(bungeeJar.elements.map { it.single().asFile }))
        output.set(layout.buildDirectory.file("deprecated/jar.jar"))
    }
    val deprecateSourcesJar = register("deprecateSourcesJar", DeprecateJar::class) {
        deprecator.set(layout.projectDirectory.file("deprecator.jar"))
        input.set(layout.file(bungeeSourcesJar.elements.map { it.single().asFile }))
        output.set(layout.buildDirectory.file("deprecated/sources.jar"))
    }
    jar {
        from(zipTree(deprecateJar.flatMap { it.output }))
    }
    sourcesJar {
        from(zipTree(deprecateSourcesJar.flatMap { it.output }))
    }
}

val javaComponent = components["java"] as AdhocComponentWithVariants
javaComponent.withVariantsFromConfiguration(configurations["javadocElements"]) {
    skip()
}

@CacheableTask
abstract class DeprecateJar : JavaExec() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val deprecator: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val input: RegularFileProperty

    @get:OutputFile
    abstract val output: RegularFileProperty

    override fun exec() {
        classpath(deprecator)
        args(input.asFile.get().absolutePath, output.asFile.get().absolutePath, "--parallelism", "1")
        super.exec()
    }
}
