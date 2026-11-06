package pl.luczka.agenttoolkit.architecture

import java.io.File

/**
 * The project's Gradle modules, read from their build scripts: Gradle's dependency graph isn't Kotlin
 * source, so Konsist can't see it. Gradle runs the tests in `:architecture-tests`' own directory, which
 * sits at the project root.
 */
internal object Modules {
    private val root: File by lazy { File(System.getProperty("user.dir")).absoluteFile.parentFile }

    /** Tooling modules the toolkit installs; they aren't part of the app's graph. */
    private val tooling = setOf(":ktlint-rules", ":architecture-tests")

    /** Every module `settings.gradle.kts` includes, at its default directory (`:data:auth` → `data/auth`). */
    val all: List<GradleModule> by lazy {
        val included = Regex("""include\(([^)]*)\)""")
            .findAll(root.resolve("settings.gradle.kts").readText())
            .flatMap { Regex(""""(:[\w:.-]+)"""").findAll(it.groupValues[1]) }
            .map { it.groupValues[1] }
        included
            .filterNot { it in tooling }
            .map { GradleModule.from(root, it) }
            .sortedBy { it.path }
            .toList()
    }

    /** The app's base package: the `:app` module's namespace, when the project has one. */
    private val baseNamespace: String? by lazy { all.firstOrNull { it.isApp }?.namespace }

    /** Konsist module name → the package its files live under; empty when there's no `:app` namespace. */
    val expectedPackages: Map<String, String> by lazy {
        val base = baseNamespace ?: return@lazy emptyMap()
        all.associate { it.konsistName to it.expectedPackage(base) }
    }
}

internal data class GradleModule(
    val path: String,
    val namespace: String?,
    val projectDependencies: Set<String>,
) {
    private val segments = path.removePrefix(":").split(':')

    val isApp get() = segments.first() == "app"
    val isCore get() = segments.first() == "core"
    val isFeature get() = segments.first() == "feature"
    val isFeatureApi get() = isFeature && segments.last() == "api"
    val isFeatureImpl get() = isFeature && segments.last() == "impl"
    val isDomain get() = segments.first() == "domain"
    val isData get() = segments.first() == "data"

    /** The feature a `:feature:<name>[:api|:impl]` module belongs to. */
    val featureName get() = if (isFeature) segments.getOrNull(1) else null

    /** The directory Konsist reports as the module name, e.g. `feature/journal/impl`. */
    val konsistName get() = segments.joinToString("/")

    /** `<base>.<module path>`: `pl.example.data.auth` for `:data:auth`; the base itself for `:app`. */
    fun expectedPackage(base: String): String = if (isApp) base else (listOf(base) + segments).joinToString(".")

    companion object {
        private val projectDependency = Regex("""project\(\s*"(:[\w:.-]+)"\s*\)""")
        private val namespaceDeclaration = Regex("""\bnamespace\s*=\s*"([\w.]+)"""")

        fun from(
            root: File,
            path: String,
        ): GradleModule {
            val buildScript = root.resolve(path.removePrefix(":").replace(':', '/')).resolve("build.gradle.kts")
            val text = if (buildScript.isFile) buildScript.readText() else ""
            return GradleModule(
                path = path,
                namespace = namespaceDeclaration.find(text)?.groupValues?.get(1),
                projectDependencies = projectDependency.findAll(text).map { it.groupValues[1] }.toSet(),
            )
        }
    }
}
