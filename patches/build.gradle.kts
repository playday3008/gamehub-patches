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
            "-Xcontext-parameters"
        )
    }
}
