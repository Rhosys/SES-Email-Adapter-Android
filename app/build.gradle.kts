plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ch.rhosys.email"
    compileSdk = 36

    defaultConfig {
        applicationId = "ch.rhosys.email"
        minSdk = 31
        targetSdk = 36
        versionCode = (findProperty("versionCode") as? String)?.toIntOrNull() ?: 1
        versionName = (findProperty("versionName") as? String) ?: "1.0.0"

        // Backend shared with the web app (SES-Email-Adapter-UI). Override
        // per-environment via gradle.properties / -P flags in CI.
        // Must keep the trailing slash: Retrofit rejects a baseUrl without one,
        // and the /api base path is what the endpoint paths (v1/*) hang off.
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${(findProperty("apiBaseUrl") as? String) ?: "https://email.rhosys.cloud/api/"}\"",
        )
        buildConfigField(
            "String",
            "AUTHRESS_CUSTOM_DOMAIN",
            "\"${(findProperty("authressDomain") as? String) ?: "login.rhosys.cloud"}\"",
        )
        buildConfigField(
            "String",
            "AUTHRESS_APPLICATION_ID",
            // Matches the web app's VITE_AUTHRESS_APPLICATION_ID; the previous
            // value was invented and no such application exists in Authress.
            "\"${(findProperty("authressApplicationId") as? String) ?: "app_2EAWGEdtzaeCj7b45DsDtt"}\"",
        )
        // Redirect target for the Authress login flow, in the scheme://host/path
        // form the SDK documents. MainActivity is the only component that claims
        // it — see its intent filter.
        buildConfigField(
            "String",
            "OAUTH_REDIRECT_URI",
            "\"ch.rhosys.email://auth/callback\"",
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("sharedDebug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("sharedDebug")
        }
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("sharedDebug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.foundation)
    implementation(libs.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin)

    implementation(libs.coil.compose)

    implementation(libs.markwon.core)
    implementation(libs.markwon.ext.strikethrough)
    implementation(libs.markwon.linkify)
    implementation(libs.markwon.html)

    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    implementation(libs.security.crypto)
    implementation(libs.biometric)
    implementation(libs.androidx.browser)

    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    implementation(libs.datastore.preferences)

    implementation(libs.posthog.android)

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    // Android's org.json is a stub that throws "not mocked" in JVM unit tests;
    // SignalEntity encodes attachments with it, so tests need a real one.
    testImplementation("org.json:json:20240303")
    testImplementation(libs.junit5.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
}
