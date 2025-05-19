plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.trellocontroller"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.trellocontroller"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "AZURE_OPENAI_API_KEY", "\"${project.findProperty("AZURE_OPENAI_API_KEY") ?: ""}\"")
        buildConfigField("String", "AZURE_OPENAI_ENDPOINT", "\"${project.findProperty("AZURE_OPENAI_ENDPOINT") ?: ""}\"")
        buildConfigField("String", "AZURE_OPENAI_DEPLOYMENT", "\"${project.findProperty("AZURE_OPENAI_DEPLOYMENT") ?: ""}\"")
        buildConfigField("String", "AZURE_OPENAI_API_VERSION", "\"${project.findProperty("AZURE_OPENAI_API_VERSION") ?: ""}\"")
        buildConfigField("String", "TRELLO_API_KEY", "\"${project.findProperty("TRELLO_API_KEY") ?: ""}\"")
        buildConfigField("String", "TRELLO_API_TOKEN", "\"${project.findProperty("TRELLO_API_TOKEN") ?: ""}\"")



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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("androidx.compose.material:material-icons-extended:<version>")
}