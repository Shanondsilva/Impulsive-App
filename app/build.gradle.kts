import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

val impulsiveVersionCode =
    providers
        .gradleProperty(
            "impulsiveVersionCode",
        )
        .orNull
        ?.toIntOrNull()
        ?.takeIf {
            it > 0
        }
        ?: error(
            "impulsiveVersionCode must be a positive integer in gradle.properties.",
        )

val impulsiveVersionName =
    providers
        .gradleProperty(
            "impulsiveVersionName",
        )
        .orNull
        ?.trim()
        ?.takeIf {
            it.isNotEmpty()
        }
        ?: error(
            "impulsiveVersionName must be defined in gradle.properties.",
        )

android {
    namespace = "com.impulsive.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.impulsive.app"
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = impulsiveVersionCode
        versionName = impulsiveVersionName

        // Safe Browse Phase 3: release rewarded-ad identifiers. Left blank when the
        // property is not supplied so a release build never silently requests a live ad
        // with a missing/fake configuration — see SafeBrowseRewardedAdController.
        buildConfigField(
            "String",
            "IMPULSIVE_ADMOB_APP_ID",
            "\"${providers.gradleProperty("IMPULSIVE_ADMOB_APP_ID").orNull.orEmpty()}\"",
        )
        buildConfigField(
            "String",
            "IMPULSIVE_SAFE_BROWSE_REWARDED_AD_UNIT_ID",
            "\"${providers.gradleProperty("IMPULSIVE_SAFE_BROWSE_REWARDED_AD_UNIT_ID").orNull.orEmpty()}\"",
        )
        // Both default to "off". A debug build alone must never force EEA consent debug
        // geography -- that has to be an explicit, deliberate opt-in via gradle.properties,
        // never an automatic side effect of `isDebuggable`.
        buildConfigField(
            "boolean",
            "IMPULSIVE_UMP_DEBUG_EEA",
            (providers.gradleProperty("IMPULSIVE_UMP_DEBUG_EEA").orNull?.toBooleanStrictOrNull() ?: false)
                .toString(),
        )
        buildConfigField(
            "String",
            "IMPULSIVE_UMP_TEST_DEVICE_HASH",
            "\"${providers.gradleProperty("IMPULSIVE_UMP_TEST_DEVICE_HASH").orNull.orEmpty()}\"",
        )
    }

    signingConfigs {
        create("release") {
            val props = Properties().apply {
                val f = rootProject.file("keystore.properties")
                if (f.exists()) {
                    f.inputStream().use(::load)
                }
            }
            storeFile = props.getProperty("storeFile")?.let { rootProject.file(it) }
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
            // Never a real AdMob app ID committed to Git, and never a placeholder fallback
            // either -- `validateSafeBrowseReleaseAdMobConfig` (tied to `preReleaseBuild`
            // below) fails the build before this placeholder is ever resolved if the
            // property is missing or malformed, so no successful release artifact can carry
            // a fake ID here.
            manifestPlaceholders["admobApplicationId"] =
                providers.gradleProperty("IMPULSIVE_ADMOB_APP_ID").orElse("")
        }
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            // Google's official sample AdMob application ID. Debug builds must never
            // request a live production advertisement.
            manifestPlaceholders["admobApplicationId"] = "ca-app-pub-3940256099942544~3347511713"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

// Google's documented AdMob application-ID and rewarded ad-unit-ID formats. Kept in sync
// with SafeBrowseAdMobAppIdPattern / SafeBrowseRewardedUnitIdPattern in
// SafeBrowseRewardedAdController.kt, which re-validates the same values at runtime as
// defence in depth.
val safeBrowseAdMobAppIdPattern = Regex("^ca-app-pub-\\d{16}~\\d{10}$")
val safeBrowseRewardedUnitIdPattern = Regex("^ca-app-pub-\\d{16}/\\d{10}$")

val validateSafeBrowseReleaseAdMobConfig by tasks.registering {
    group = "verification"
    description = "Fails a release build if the AdMob app ID or Safe Browse rewarded ad " +
        "unit ID is missing or does not match Google's official ID format."

    val admobAppIdProvider = providers.gradleProperty("IMPULSIVE_ADMOB_APP_ID")
    val rewardedUnitIdProvider = providers.gradleProperty("IMPULSIVE_SAFE_BROWSE_REWARDED_AD_UNIT_ID")

    doLast {
        val admobAppId = admobAppIdProvider.orNull
        if (admobAppId == null || !safeBrowseAdMobAppIdPattern.matches(admobAppId)) {
            throw GradleException(
                "IMPULSIVE_ADMOB_APP_ID must be set in gradle.properties and match " +
                    "${safeBrowseAdMobAppIdPattern.pattern} for a release build. " +
                    "Found: ${admobAppId ?: "<missing>"}",
            )
        }

        val rewardedUnitId = rewardedUnitIdProvider.orNull
        if (rewardedUnitId == null || !safeBrowseRewardedUnitIdPattern.matches(rewardedUnitId)) {
            throw GradleException(
                "IMPULSIVE_SAFE_BROWSE_REWARDED_AD_UNIT_ID must be set in gradle.properties " +
                    "and match ${safeBrowseRewardedUnitIdPattern.pattern} for a release " +
                    "build. Found: ${rewardedUnitId ?: "<missing>"}",
            )
        }
    }
}

// Tied to preReleaseBuild (a release-build-type-only lifecycle task) rather than run
// unconditionally, so a debug build or unit test run never requires production AdMob IDs
// to be configured.
afterEvaluate {
    tasks.named("preReleaseBuild") {
        dependsOn(validateSafeBrowseReleaseAdMobConfig)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.sqlite)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.work.runtime.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.lottie.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.crashlytics)
    releaseImplementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)

    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.identity.googleid)
    implementation(libs.facebook.login)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.google.play.services.auth)
    implementation(libs.billing.ktx)
    implementation(libs.play.review)
    implementation(libs.play.review.ktx)
    implementation(libs.okhttp)
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.json:json:20251224")
    // Compose UI tests exercise the same semantics tree assistive tech reads.
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
}
