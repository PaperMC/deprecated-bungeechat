plugins {
    id("net.kyori.indra") version "2.1.1"
    `maven-publish`
}

group = "net.md-5"
val ghRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.let { "build.$it" } ?: "local"
val bungeeVersion = "1.21-R0.2"
version = if (bungeeVersion.endsWith("-SNAPSHOT")) {
    "${bungeeVersion.replace("-SNAPSHOT", "")}-deprecated-SNAPSHOT"
} else {
    "$bungeeVersion-deprecated+$ghRunNumber"
}
val deprecation = "BungeeCord Chat API has been deprecated in favor of Adventure API. For help with migrating your code, see <a href=\"https://docs.advntr.dev/migration/bungeecord-chat-api.html\">https://docs.advntr.dev/migration/bungeecord-chat-api.html</a>"

indra {
    javaVersions().target(8)
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

    // bungee deps (keep in sync with bungee version)
    api("com.google.code.gson:gson:2.8.8")
    api("com.google.guava:guava:21.0")
}

tasks {
    // deprecator.jar is jpenilla/jar-deprecate fat jar
    val deprecateJar = register("deprecateJar", DeprecateJar::class) {
        deprecator.set(layout.projectDirectory.file("deprecator.jar"))
        input.set(layout.file(bungeeJar.elements.map { it.single().asFile }))
        output.set(layout.buildDirectory.file("deprecated/jar.jar"))
        deprecationMessage.set(deprecation)
    }
    val deprecateSourcesJar = register("deprecateSourcesJar", DeprecateJar::class) {
        deprecator.set(layout.projectDirectory.file("deprecator.jar"))
        input.set(layout.file(bungeeSourcesJar.elements.map { it.single().asFile }))
        output.set(layout.buildDirectory.file("deprecated/sources.jar"))
        deprecationMessage.set(deprecation)
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

publishing {
    val url = if (bungeeVersion.endsWith("-SNAPSHOT")) {
        "https://repo.papermc.io/repository/maven-snapshots/"
    } else {
        "https://repo.papermc.io/repository/maven-releases/"
    }
    repositories.maven(url) {
        name = "paper"
        credentials(PasswordCredentials::class)
    }
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
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

    @get:Input
    abstract val deprecationMessage: Property<String>

    override fun exec() {
        classpath(deprecator)
        args(input.asFile.get().absolutePath, output.asFile.get().absolutePath, "--parallelism", "1", "--message", deprecationMessage.get())
        super.exec()
    }
}
