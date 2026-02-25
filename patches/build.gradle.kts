group = "com.github.playday3008"

patches {
    about {
        name = "GameHub for ReVanced"
        description = "GameHub Patches for ReVanced"
        source = "git@github.com:playday3008/gamehub-patches.git"
        author = "PlayDay"
        contact = "https://github.com/playday3008"
        website = "https://github.com/playday3008/gamehub-patches"
        license = "GNU General Public License v3.0"
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexplicit-backing-fields",
            "-Xcontext-parameters",
        )
    }
}

// Fix: Gradle 9.x Tooling API exclusive lock error during IDE sync.
// The revanced-patches-gradle-plugin adds a resolvable extensionConfiguration
// to sourceSets.main.resources, which gets resolved when IDEs query source dirs.
// Moving it to a processResources input defers resolution to task execution time.
afterEvaluate {
    val extConfig = configurations.findByName("extensionConfiguration") ?: return@afterEvaluate

    sourceSets.named("main") {
        resources.setSrcDirs(listOf("src/main/resources"))
    }

    tasks.named<Copy>("processResources") {
        from(extConfig)
    }
}
