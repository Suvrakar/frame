import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Release configuration comes from keystore.properties (git-ignored) or environment variables,
// so secrets never live in the repository. See README.md.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun secret(name: String, env: String): String? =
    (keystoreProperties.getProperty(name) ?: System.getenv(env))?.takeIf { it.isNotBlank() }

// Google's public test IDs. Debug builds always use them; release builds use real IDs when provided.
val testAdmobAppId = "ca-app-pub-3940256099942544~3347511713"
val testAdmobBannerId = "ca-app-pub-3940256099942544/9214589741"
val releaseAdmobAppId = secret("admobAppId", "FRAME_ADMOB_APP_ID")
val releaseAdmobBannerId = secret("admobBannerId", "FRAME_ADMOB_BANNER_ID")

android {
    namespace = "app.frame.studio"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.frame.studio"
        minSdk = 26
        targetSdk = 36
        versionCode = (findProperty("frame.versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("frame.versionName") as String?) ?: "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storePath = secret("storeFile", "FRAME_KEYSTORE_PATH")
            if (storePath != null) {
                storeFile = rootProject.file(storePath)
                storePassword = secret("storePassword", "FRAME_KEYSTORE_PASSWORD")
                keyAlias = secret("keyAlias", "FRAME_KEY_ALIAS")
                keyPassword = secret("keyPassword", "FRAME_KEY_PASSWORD")
            }
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
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) signingConfig = releaseSigning
            resValue("string", "admob_app_id", releaseAdmobAppId ?: testAdmobAppId)
            resValue("string", "admob_banner_id", releaseAdmobBannerId ?: testAdmobBannerId)
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "admob_app_id", testAdmobAppId)
            resValue("string", "admob_banner_id", testAdmobBannerId)
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        checkReleaseBuilds = true
        abortOnError = true
        textReport = true
        textOutput = file("stdout")
        // Translations are not shipped yet.
        disable += listOf("MissingTranslation")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

gradle.taskGraph.whenReady {
    val buildingRelease = allTasks.any { it.name.contains("Release") && it.project == project }
    if (buildingRelease && (releaseAdmobAppId == null || releaseAdmobBannerId == null)) {
        logger.warn("WARNING: Release build is using Google's TEST AdMob IDs. Set admobAppId/admobBannerId before publishing.")
    }
    if (buildingRelease && android.signingConfigs.getByName("release").storeFile == null) {
        logger.warn("WARNING: No release keystore configured; release outputs will be unsigned.")
    }
}

dependencies {
    // Pinned: the editor preview uses CompositionPlayer (see PreviewController).
    val media3 = "1.8.0"

    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.navigation:navigation-compose:2.9.3")

    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-transformer:$media3")
    implementation("androidx.media3:media3-effect:$media3")
    implementation("androidx.media3:media3-common:$media3")
    implementation("androidx.media3:media3-muxer:$media3")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("com.google.android.gms:play-services-ads:24.5.0")
    implementation("com.google.android.ump:user-messaging-platform:3.2.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
