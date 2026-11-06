plugins {
    id("fabric-loom") version "1.11-SNAPSHOT"
}

version = property("mod_version") as String
group = "com.github.tadaooq123"

base {
    archivesName.set("BlindDeafMute-fabric-${property("minecraft_version")}")
}

repositories {
    // Maven Central часто отвечает 429 — используем зеркало PaperMC (проксирует Central)
    removeIf { it is MavenArtifactRepository && it.url.host == "repo.maven.apache.org" }
    maven("https://repo.papermc.io/repository/maven-public/") { name = "MavenCentralMirror" }
    maven("https://maven.maxhenkel.de/repository/public") { name = "henkelmax" }
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    compileOnly("de.maxhenkel.voicechat:voicechat-api:${property("voicechat_api_version")}")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    inputs.property("version", project.version)
    filteringCharset = "UTF-8"
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
