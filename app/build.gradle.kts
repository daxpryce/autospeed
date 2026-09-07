plugins {
    alias(libs.plugins.android.application)
}

// The exact acknowledgment text a personal-profile release build must find in the
// AUTOSPEED_PERSONAL_USE_ACK environment variable (design section 2.1). This is intentionally
// duplicated (not imported) from io.pryce.android.autospeed.core.build.PersonalUseAcknowledgment.REQUIRED_TEXT:
// Gradle build scripts cannot reference compiled classes from a project dependency at
// configuration time, only at task-execution runtime classpath. Keep the two literals identical.
val personalUseAcknowledgmentRequiredText =
    "Do not configure or interact with Autospeed while driving. " +
        "Obey applicable laws and remain attentive."

/**
 * Release signing material, supplied entirely through the environment (see `docs/releases.md`).
 *
 * Nothing about the signing key may be committed, so there is no keystore path, alias, or
 * password anywhere in this repository. When the variables are absent the signing configuration
 * is simply not created, which keeps ordinary debug work and `scripts/check` running on a clean
 * clone; `scripts/release-build` is what insists on the variables being present.
 */
val signingStoreFile: String? = System.getenv("AUTOSPEED_SIGNING_STORE_FILE")
val signingStorePassword: String? = System.getenv("AUTOSPEED_SIGNING_STORE_PASSWORD")
val signingKeyAlias: String? = System.getenv("AUTOSPEED_SIGNING_KEY_ALIAS")
val signingKeyPassword: String? = System.getenv("AUTOSPEED_SIGNING_KEY_PASSWORD")
val releaseSigningAvailable =
    !signingStoreFile.isNullOrBlank() &&
        !signingStorePassword.isNullOrBlank() &&
        !signingKeyAlias.isNullOrBlank() &&
        !signingKeyPassword.isNullOrBlank()

// Overridable so a tagged release can carry the tag's version rather than whatever was last
// committed here. An unset variable falls back to the development defaults, but a variable that
// is set and unusable is a configuration error: silently shipping versionCode 1 would produce a
// release Android treats as a downgrade of every prior install, which is not recoverable by
// republishing under the same version.
val releaseVersionName: String = System.getenv("AUTOSPEED_VERSION_NAME") ?: "0.1.0"
val releaseVersionCode: Int =
    System.getenv("AUTOSPEED_VERSION_CODE")?.let { raw ->
        raw.toIntOrNull()?.takeIf { it > 0 }
            ?: throw GradleException(
                "AUTOSPEED_VERSION_CODE must be a positive integer, but was \"$raw\".",
            )
    } ?: 1

android {
    namespace = "io.pryce.android.autospeed"
    // Design section 2: minimum API 36, target and compile against API 37. The Android SDK now
    // versions platforms with a minor component, so API 37 is published as "android-37.0" and is
    // selected here with compileSdk/compileSdkMinor.
    compileSdk = 37
    compileSdkMinor = 0
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "io.pryce.android.autospeed"
        minSdk = 36
        targetSdk = 37
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += listOf("advisory", "location")

    productFlavors {
        create("public") {
            dimension = "advisory"
            resValue("string", "advisory_profile", "public")
        }
        create("personal") {
            dimension = "advisory"
            applicationIdSuffix = ".personal"
            resValue("string", "advisory_profile", "personal")
        }
        create("framework") {
            dimension = "location"
            resValue("string", "location_backend", "framework")
        }
        create("play") {
            dimension = "location"
            resValue("string", "location_backend", "play")
        }
    }

    if (releaseSigningAvailable) {
        signingConfigs {
            create("release") {
                storeFile = file(signingStoreFile!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
                // v3 only. v1 (JAR signing) is consulted only below API 24 and v2 only below
                // API 28, while minSdk here is 36, so neither can ever be reached. v3 also
                // permits key rotation later without orphaning installs.
                enableV1Signing = false
                enableV2Signing = false
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSigningAvailable) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = false
        resValues = true
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkReleaseBuilds = true
        checkDependencies = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    add("playImplementation", libs.play.services.location)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
}

// Design section 2.1: producing a personal release must be an explicit build-time act that fails
// unless the environment contains AUTOSPEED_PERSONAL_USE_ACK matching the public advisory text
// exactly. This is not a secret and must not gate anything except the build itself.
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val isPersonal = variant.productFlavors.any { (_, flavorName) -> flavorName == "personal" }
        if (isPersonal) {
            val variantNameCapitalized = variant.name.replaceFirstChar { it.uppercase() }
            val checkTask =
                tasks.register("check${variantNameCapitalized}PersonalUseAcknowledgment") {
                    group = "verification"
                    description =
                        "Fails unless AUTOSPEED_PERSONAL_USE_ACK exactly matches the required " +
                        "personal-use acknowledgment text (design section 2.1)."
                    doLast {
                        val actual = providers.environmentVariable("AUTOSPEED_PERSONAL_USE_ACK").orNull
                        check(actual == personalUseAcknowledgmentRequiredText) {
                            "AUTOSPEED_PERSONAL_USE_ACK must exactly equal the required personal-use " +
                                "acknowledgment text to build a personal release artifact. See " +
                                "docs/autospeed-design.md section 2.1. A public release does not " +
                                "require this variable."
                        }
                    }
                }
            afterEvaluate {
                tasks
                    .matching { task ->
                        task.name == "assemble$variantNameCapitalized" || task.name == "bundle$variantNameCapitalized"
                    }.configureEach {
                        dependsOn(checkTask)
                    }
            }
        }
    }
}
