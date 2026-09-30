import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kotlin.serialization)
}

val secretsFile = rootProject.file("secrets.properties").takeIf { it.exists() }
    ?: rootProject.file("local.properties")

val secrets = Properties().apply {
    if (secretsFile.exists()) {
        load(FileInputStream(secretsFile))
    }
}

fun getSecret(key: String, fallback: String = ""): String =
    secrets.getProperty(key, fallback).trim()

android {
    namespace = "com.example.callog"

    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.callog"
        minSdk = 29
        targetSdk = 36

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Supabase Dev
        buildConfigField("String", "SUPABASE_DEV_URL", "\"${getSecret("supabase.dev.url", "https://qizrtmvgcwxuycbpkeua.supabase.co")}\"")
        buildConfigField("String", "SUPABASE_DEV_ANON_KEY", "\"${getSecret("supabase.dev.anon_key", "")}\"")

        // Supabase Prod
        buildConfigField("String", "SUPABASE_PROD_URL", "\"${getSecret("supabase.prod.url", "https://nmhgdhehsfcootlqwumw.supabase.co")}\"")
        buildConfigField("String", "SUPABASE_PROD_ANON_KEY", "\"${getSecret("supabase.prod.anon_key", "")}\"")

        // Firebase Dev
        buildConfigField("String", "FIREBASE_DEV_PROJECT_ID", "\"${getSecret("firebase.dev.project_id", "allset-491218")}\"")
        buildConfigField("String", "FIREBASE_DEV_API_KEY", "\"${getSecret("firebase.dev.api_key", "AIzaSyBtlY7EoO6PgPUCMjNR55K88H2v665qQgQ")}\"")
        buildConfigField("String", "FIREBASE_DEV_APP_ID", "\"${getSecret("firebase.dev.app_id", "1:799427430422:android:e0f5737f12b8b9cbb20d37")}\"")
        buildConfigField("String", "FIREBASE_DEV_STORAGE_BUCKET", "\"${getSecret("firebase.dev.storage_bucket", "allset-491218.firebasestorage.app")}\"")
        buildConfigField("String", "FIREBASE_DEV_GCM_SENDER_ID", "\"${getSecret("firebase.dev.gcm_sender_id", "799427430422")}\"")
        buildConfigField("String", "FIREBASE_DEV_DATABASE_URL", "\"${getSecret("firebase.dev.database_url", "https://allset-491218-default-rtdb.asia-southeast1.firebasedatabase.app")}\"")
        buildConfigField("String", "FIREBASE_DEV_DATABASE_ID", "\"${getSecret("firebase.dev.database_id", "call-logs")}\"")

        // Firebase Prod
        buildConfigField("String", "FIREBASE_PROD_PROJECT_ID", "\"${getSecret("firebase.prod.project_id", "allset-244ab")}\"")
        buildConfigField("String", "FIREBASE_PROD_API_KEY", "\"${getSecret("firebase.prod.api_key", "AIzaSyDLqHOgAdLBz9__Un_BI0ZMwz5l2ucJnKo")}\"")
        buildConfigField("String", "FIREBASE_PROD_APP_ID", "\"${getSecret("firebase.prod.app_id", "1:598633676290:android:e264397de748bf0371711b")}\"")
        buildConfigField("String", "FIREBASE_PROD_STORAGE_BUCKET", "\"${getSecret("firebase.prod.storage_bucket", "allset-244ab.firebasestorage.app")}\"")
        buildConfigField("String", "FIREBASE_PROD_GCM_SENDER_ID", "\"${getSecret("firebase.prod.gcm_sender_id", "598633676290")}\"")
        buildConfigField("String", "FIREBASE_PROD_DATABASE_ID", "\"${getSecret("firebase.prod.database_id", "call-logs")}\"")
    }

    buildFeatures {
        compose = true
        resValues = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

    flavorDimensions += "environment"

    productFlavors {
        create("dev") {
            dimension = "environment"
            resValue("string", "app_environment", "Development")
        }
        create("prod") {
            dimension = "environment"
            resValue("string", "app_environment", "Production")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.compose.ui)

    implementation(libs.androidx.compose.ui.graphics)

    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Dagger Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.auth)
    implementation(libs.androidx.work.runtime)
    implementation(platform("com.google.firebase:firebase-bom:34.15.0"))
    implementation("com.google.firebase:firebase-analytics")

    // Media3 Player
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // Coil Image Loader
    implementation(libs.coil.compose)
    implementation(libs.androidx.compose.ui.text.google.fonts)

    // Supabase & Serialization
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)

    androidTestImplementation(libs.androidx.espresso.core)

    androidTestImplementation(platform(libs.androidx.compose.bom))

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
