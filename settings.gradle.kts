rootProject.name = "gamehub-patches"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        maven {
            name = "githubPackages"
            url = uri("https://maven.pkg.github.com/revanced/revanced-patches-template")
            credentials(PasswordCredentials::class)
        }
    }
}

plugins {
    id("app.revanced.patches") version "1.0.0-dev.8"
}

settings {
    extensions {
        defaultNamespace = "app.revanced.extension"
    }
}
