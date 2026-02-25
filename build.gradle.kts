plugins {
    id("com.diffplug.spotless") version "8.2.1"
}

allprojects {
    apply(plugin = "com.diffplug.spotless")
    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        if (path.startsWith(":extensions:")) {
            java {
                target("src/**/*.java")
                googleJavaFormat().aosp()
            }
        }
        if (path == ":patches") {
            kotlin {
                target("src/**/*.kt")
                ktlint()
            }
        }
        kotlinGradle {
            ktlint()
        }
    }
    // Apply eclipse plugin to Android extension modules so Buildship/JDT LS
    // gets correct source directories (AGP 9.x doesn't expose source sets
    // through the Gradle Tooling API model that Buildship understands).
    if (path.startsWith(":extensions:")) {
        apply(plugin = "eclipse")
        afterEvaluate {
            // Resolve android.jar from the Android SDK for eclipse classpath.
            // Try local.properties first, then ANDROID_HOME / ANDROID_SDK_ROOT env vars.
            val sdkDir = rootProject
                .file("local.properties")
                .takeIf { it.exists() }
                ?.let { f ->
                    val props = java.util.Properties().apply { f.inputStream().use { load(it) } }
                    props.getProperty("sdk.dir")?.let(::File)
                }
                ?: System.getenv("ANDROID_HOME")?.let(::File)
                ?: System.getenv("ANDROID_SDK_ROOT")?.let(::File)

            val androidJar = sdkDir
                ?.resolve("platforms")
                ?.listFiles()
                ?.filter { it.resolve("android.jar").exists() }
                ?.maxByOrNull { it.name }
                ?.resolve("android.jar")

            extensions.configure<org.gradle.plugins.ide.eclipse.model.EclipseModel> {
                classpath {
                    file {
                        whenMerged {
                            val cp = this as org.gradle.plugins.ide.eclipse.model.Classpath
                            // Add src/main/java source entry.
                            cp.entries.removeAll {
                                it is org.gradle.plugins.ide.eclipse.model.SourceFolder &&
                                    it.path == "src/main/java"
                            }
                            cp.entries.add(
                                org.gradle.plugins.ide.eclipse.model.SourceFolder(
                                    "src/main/java",
                                    "bin/main",
                                ),
                            )
                            // Add compileOnly project dependencies as project refs.
                            configurations
                                .findByName("compileOnly")
                                ?.dependencies
                                ?.filterIsInstance<ProjectDependency>()
                                ?.forEach { dep ->
                                    val depName = (dep as ProjectDependency).name
                                    val hasProject = cp.entries.any {
                                        it is org.gradle.plugins.ide.eclipse.model.ProjectDependency &&
                                            it.path == "/$depName"
                                    }
                                    if (!hasProject) {
                                        cp.entries.add(
                                            org.gradle.plugins.ide.eclipse.model.ProjectDependency(
                                                "/$depName",
                                            ),
                                        )
                                    }
                                }
                            // Add android.jar as a library.
                            if (androidJar?.exists() == true) {
                                val hasAndroid = cp.entries.any {
                                    it is org.gradle.plugins.ide.eclipse.model.Library &&
                                        it.path.contains("android.jar")
                                }
                                if (!hasAndroid) {
                                    cp.entries.add(
                                        org.gradle.plugins.ide.eclipse.model.Library(
                                            org.gradle.plugins.ide.eclipse.model.internal
                                                .FileReferenceFactory()
                                                .fromPath(androidJar.absolutePath),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    tasks.withType(JavaCompile::class) {
        options.compilerArgs.add("-Xlint:unchecked")
        options.compilerArgs.add("-Xlint:deprecation")
    }
}
