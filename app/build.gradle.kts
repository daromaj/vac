plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.vac"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.vac"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
    
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.preference)

    // Required for call screening
    implementation(libs.concurrent.futures)
    
    // JSON handling
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Android core
    implementation("androidx.core:core:1.12.0")
    implementation("androidx.annotation:annotation:1.7.1")
    
    // Audio recording
    implementation("androidx.media:media:1.7.0")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test:runner:1.5.2")
    testImplementation("androidx.test:rules:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("org.mockito:mockito-core:5.10.0")
    
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}