import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Play Integrity — the Google Cloud project number linked to this app in Play
// Console (Setup > API access). Resolved from `local.properties`
// (playIntegrityCloudProjectNumber=...), which is gitignored and never
// committed, or from the PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER env var (CI).
// Falls back to 0L — a placeholder that fails loudly at runtime (see
// PlayIntegrityTokenProvider) rather than silently, and that a release build
// refuses to ship (see the assembleRelease/bundleRelease guard below).
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        FileInputStream(file).use { load(it) }
    }
}

val playIntegrityCloudProjectNumber: Long =
    (localProperties.getProperty("playIntegrityCloudProjectNumber") ?: System.getenv("PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER"))
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.toLongOrNull()
        ?: 0L

android {
    namespace = "com.owlmedia.racecontrol"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.owlmedia.racecontrol"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField(
            "long",
            "PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER",
            "${playIntegrityCloudProjectNumber}L",
        )
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // minSdk 26 predates java.time; desugaring gives us the full API.
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.work.runtime.ktx)

    // Play Integrity — Android counterpart of iOS App Attest.
    implementation(libs.play.integrity)

    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}

// A release build shipping the 0L Play Integrity placeholder would silently
// degrade to unauthenticated requests in production (see PlayIntegrityTokenProvider
// and the buildConfigField above) rather than failing anywhere obvious. Fail the
// build itself instead, before it ever reaches a device.
tasks.matching { it.name in setOf("assembleRelease", "bundleRelease") }.configureEach {
    doFirst {
        if (playIntegrityCloudProjectNumber == 0L) {
            throw GradleException(
                "PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER is still the placeholder 0L - set it in " +
                    "local.properties (playIntegrityCloudProjectNumber=...) or the " +
                    "PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER env var before a release build.",
            )
        }
    }
}
