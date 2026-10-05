import org.gradle.kotlin.dsl.implementation

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
  //  alias(libs.plugins.hilt)
    kotlin("kapt")
    id("kotlin-parcelize")
}

android {
    namespace = "com.mobile.trackerapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mobile.trackerapp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Ads & Billing
        val erain_studio_version = "2.1"
        val module_update_gdpr_version = "2.0.2"
        val play_review_ktx_version = "2.0.2"
        val play_services_ads_version = "24.7.0"
        val billing_version = "8.0.0"

        debug {
            buildConfigField("String", "email_feedback", "\"glorymobile88@gmail.com\"")

            buildConfigField("String", "ERAIN_STUDIO_VERSION", "\"$erain_studio_version\"")
            buildConfigField("String", "PLAY_SERVICES_ADS_VERSION", "\"$play_services_ads_version\"")
            buildConfigField("String", "GDPR_MODULE_VERSION", "\"$module_update_gdpr_version\"")
        }
        release {
            buildConfigField("String", "ERAIN_STUDIO_VERSION", "\"$erain_studio_version\"")
            buildConfigField("String", "PLAY_SERVICES_ADS_VERSION", "\"$play_services_ads_version\"")
            buildConfigField("String", "GDPR_MODULE_VERSION", "\"$module_update_gdpr_version\"")
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }
    buildFeatures {
        compose = true
        buildConfig = true
        dataBinding = true
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.config)
    implementation(libs.onesignal)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
   // implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
  //  kapt(libs.hilt.compiler)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // Moshi
    val moshiVersion = "1.15.2"
    kapt("com.squareup.moshi:moshi-kotlin-codegen:$moshiVersion")
    implementation("com.squareup.moshi:moshi:$moshiVersion")
    implementation("com.squareup.moshi:moshi-kotlin:$moshiVersion")
    implementation("com.squareup.moshi:moshi-adapters:$moshiVersion")

    // Timber
    val timber_version = "5.0.1"
    implementation("com.jakewharton.timber:timber:${timber_version}")

    val glide_version = "4.15.1"
    implementation("com.github.bumptech.glide:glide:$glide_version")
    kapt("com.github.bumptech.glide:compiler:$glide_version")

    // Lifecycle
    val  androidx_lifecycle_version = "2.6.1"
    val androidx_fragment_ktx_version = "1.6.1"
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:${androidx_lifecycle_version}")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:${androidx_lifecycle_version}")
    implementation("androidx.fragment:fragment-ktx:${androidx_fragment_ktx_version}")


    val erain_studio_version = "2.1"
    val module_update_gdpr_version = "2.0.2"
    val play_review_ktx_version = "2.0.2"
    val play_services_ads_version = "24.7.0"
    val shimmer_version = "0.5.0"
    val billing_version = "8.0.0"
    val androidx_multidex_version = "2.0.1"
    implementation("com.github.trongluan99:ERain-Studio:${erain_studio_version}")
    implementation("com.github.Infinity-Technologies-Global:DevConfig:1.0.7-partner")
    implementation("com.github.Infinity-Technologies-Global:Module-Update-GDPR:${module_update_gdpr_version}")
    implementation("com.google.android.play:review-ktx:${play_review_ktx_version}")
    implementation("com.facebook.shimmer:shimmer:${shimmer_version}")
    implementation("com.google.android.gms:play-services-ads:${play_services_ads_version}")
    implementation("androidx.multidex:multidex:${androidx_multidex_version}")
}
