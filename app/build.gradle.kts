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
        versionCode = 1
        versionName = "0.1.0"
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

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
