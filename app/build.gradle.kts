plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.valpr.bikecompanion"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.valpr.bikecompanion"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Required for Robolectric-hosted compose semantics tests: AGP must pass
    // the merged manifest + resources to the local unit-test JVM.
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Plain-JUnit suites are independent (isolated temp dirs / schedulers);
        // spread classes across forks so the Robolectric UI class doesn't
        // serialize the whole suite. Heap headroom is max, not preallocated.
        unitTests.all {
            it.maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(2)
            it.maxHeapSize = "2g"
        }
    }

    signingConfigs {
        create("release") {
            // Optional: only used when release.keystore / KEYSTORE_FILE is set.
            // Otherwise the release build stays unsigned (assembleDebug unaffected).
            val keystorePath =
                System.getenv("KEYSTORE_FILE")
                    ?: project.findProperty("release.keystore")?.toString()
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                    ?: project.findProperty("release.storePassword")?.toString()
                keyAlias = System.getenv("KEY_ALIAS")
                    ?: project.findProperty("release.keyAlias")?.toString()
                keyPassword = System.getenv("KEY_PASSWORD")
                    ?: project.findProperty("release.keyPassword")?.toString()
            }
        }
    }
    buildTypes {
        debug {
            versionNameSuffix = "-debug"
        }
        release {
            val keystorePath =
                System.getenv("KEYSTORE_FILE")
                    ?: project.findProperty("release.keystore")?.toString()
            if (!keystorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        lintConfig = rootProject.file("lint.xml")
        abortOnError = true
        checkReleaseBuilds = false
        warningsAsErrors = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    // Required by lintVitalRelease: ActivityResult APIs need Fragment >= 1.3.0
    implementation(libs.androidx.fragment)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(project(":shared"))
    implementation(libs.play.services.wearable)
    implementation(libs.androidx.health.connect.client)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kxml2)
    testImplementation(libs.xmlpull)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.ui.test.manifest)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
