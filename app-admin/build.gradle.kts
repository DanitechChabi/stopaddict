plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

// Valeurs PUBLIQUES (URL + clé publishable). Surchageables par des propriétés
// Gradle (-PSUPABASE_URL=... -PSUPABASE_PUBLISHABLE_KEY=...).
val supabaseUrl = (project.findProperty("SUPABASE_URL") as String?)
    ?: "https://mizdmvklljjayspcnevq.supabase.co"
val supabaseKey = (project.findProperty("SUPABASE_PUBLISHABLE_KEY") as String?)
    ?: "sb_publishable_I3BUBWqEIKxfn27EMPPkrA_PcFsOvWG"

android {
    namespace = "com.stopaddict.admin"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.stopaddict.admin"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseKey\"")
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
}
