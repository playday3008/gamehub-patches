// This file exists so Dependabot's Gradle file fetcher can discover the project.

subprojects {
    tasks.withType(JavaCompile::class) {
        options.compilerArgs.add("-Xlint:unchecked")
        options.compilerArgs.add("-Xlint:deprecation")
    }
}
