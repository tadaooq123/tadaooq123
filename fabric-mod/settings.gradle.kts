pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://repo.papermc.io/repository/maven-public/") { name = "MavenCentralMirror" }
        gradlePluginPortal()
    }
}

rootProject.name = "BlindDeafMute-Fabric"
