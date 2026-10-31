plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

// Valeurs PUBLIQUES, surchargeables par propriétés Gradle.
val supabaseUrl = (project.findProperty("SUPABASE_URL") as String?)
    ?: "https://mizdmvklljjayspcnevq.supabase.co"
val supabaseKey = (project.findProperty("SUPABASE_PUBLISHABLE_KEY") as String?)
    ?: "sb_publishable_I3BUBWqEIKxfn27EMPPkrA_PcFsOvWG"
// Clé publique Ed25519 (SPKI base64) pour vérifier les signatures serveur.
// Vide par défaut : tant qu'elle n'est pas fournie, le statut reste prudent.
val signingPublicKey = (project.findProperty("SIGNING_PUBLIC_KEY_SPKI_B64") as String?) ?: ""

android {
    namespace = "com.stopaddict.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.stopaddict.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseKey\"")
        buildConfigField("String", "SIGNING_PUBLIC_KEY_SPKI_B64", "\"$signingPublicKey\"")
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
    implementation(project(":core:crypto"))
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
