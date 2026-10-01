plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.valpr.bikecompanion.wear"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.valpr.bikecompanion"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
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
    // Required for Robolectric-hosted compose tests: AGP must pass the merged
    // manifest + resources to the local unit-test JVM (mirrors :app).
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    // Keep in sync with :app — lintVitalRelease requires Fragment >= 1.3.0
    implementation(libs.androidx.fragment)

    implementation(libs.play.services.wearable)
    implementation(libs.androidx.wear)
    implementation(libs.androidx.wear.ongoing)
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.navigation)

    implementation(libs.androidx.health.services.client)
    implementation(libs.androidx.concurrent.futures.ktx)
    implementation(libs.kotlinx.coroutines.guava)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // Provides the test ComponentActivity for compose rules under Robolectric.
    testImplementation(libs.androidx.ui.test.manifest)
    debugImplementation(libs.androidx.ui.test.manifest)
}
