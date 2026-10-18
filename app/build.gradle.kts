plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.meilluer.glanceables"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.meilluer.glanceables"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {

            val keystorePath = System.getenv("CM_KEYSTORE_PATH")
            val keystorePassword = System.getenv("CM_KEYSTORE_PASSWORD")
            val keyAliasEnv = System.getenv("CM_KEY_ALIAS")
            val keyPasswordEnv = System.getenv("CM_KEY_PASSWORD")

            if (
                keystorePath != null &&
                keystorePassword != null &&
                keyAliasEnv != null &&
                keyPasswordEnv != null
            ) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = keyAliasEnv
                keyPassword = keyPasswordEnv
            }
        }
    }


    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.lifecycle.runtime)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Background work
    implementation(libs.androidx.work.runtime)

    // Wearable Data Layer
    implementation(libs.play.services.wearable)

    // Location
    implementation(libs.play.services.location)

    // PDF extraction
    implementation(libs.pdfbox.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}