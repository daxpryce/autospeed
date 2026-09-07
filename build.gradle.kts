import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.kover) apply false
}

allprojects {
    group = "io.pryce.android"
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    dependencyLocking {
        lockAllConfigurations()
    }

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.8.0")
        debug.set(false)
        verbose.set(true)
        android.set(false)
        outputToConsole.set(true)
        ignoreFailures.set(false)
        enableExperimentalRules.set(false)
        additionalEditorconfig.putAll(
            provider {
                rootProject.file("config/ktlint/.editorconfig").readLines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("[") && !it.startsWith("root") }
                    .mapNotNull { line ->
                        val parts = line.split("=", limit = 2)
                        if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
                    }.toMap()
            },
        )
        filter {
            exclude { entry -> entry.file.path.contains("${File.separator}build${File.separator}") }
        }
    }

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        parallel = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        baseline = rootProject.file("config/detekt/baseline-${project.name}.xml").takeIf { it.exists() }
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(true)
            sarif.required.set(false)
            txt.required.set(false)
            md.required.set(false)
        }
        jvmTarget = "17"
        // Detekt 1.23.8 runs its analysis in-process on the Gradle daemon's own JVM by default
        // (its `jdkHome`/toolchain support is wired for other tasks but never actually applied to
        // this task's forked-worker option, so pinning a different toolchain has no effect here).
        // Its bundled Kotlin compiler frontend calls `JavaVersion.parse(System.getProperty(
        // "java.version"))`, which throws on JDK 25's four-component version string (e.g.
        // "25.0.4.1"); the JDK's own reported value is otherwise unused by detekt, so briefly
        // substituting a parseable value for the duration of this task is safe.
        doFirst {
            System.setProperty("java.version", "21.0.1")
        }
    }

    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
                allWarningsAsErrors.set(true)
            }
        }
    }

    // AGP 9's built-in Kotlin support (design decision: the explicit `org.jetbrains.kotlin.android`
    // plugin is incompatible with AGP 9's new DSL and fails to apply; AGP registers this same
    // `kotlin` extension itself once `com.android.application` is applied, using whichever Kotlin
    // compiler version AGP 9.2.1 itself bundles).
    plugins.withId("com.android.application") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
                allWarningsAsErrors.set(true)
            }
        }
    }
}
