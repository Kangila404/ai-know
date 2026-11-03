import java.util.Properties
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val config = Properties().apply { rootProject.file("app-config.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
fun setting(name: String, fallback: String = "") = providers.gradleProperty(name).orNull ?: config.getProperty(name, fallback)
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")
android {
    namespace = "org.aiknow.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.aiknow.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", quoted(setting("API_BASE_URL", "https://aiknow.kro.kr/")))
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", quoted(setting("GOOGLE_WEB_CLIENT_ID")))
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", quoted(setting("KAKAO_NATIVE_APP_KEY")))
        manifestPlaceholders["kakaoScheme"] = "kakao" + setting("KAKAO_NATIVE_APP_KEY", "unconfigured")
    }
    flavorDimensions += "environment"
    productFlavors {
        create("live") { dimension = "environment" }
        create("demo") { dimension = "environment"; applicationIdSuffix = ".demo"; versionNameSuffix = "-demo" }
    }
    buildTypes {
        release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
androidComponents { beforeVariants(selector().withBuildType("release")) { if (it.productFlavors.any { f -> f.second == "demo" }) it.enable = false } }
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.01")
    implementation(composeBom); androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.kakao.sdk:v2-user:2.26.0")
    implementation(platform("com.google.firebase:firebase-bom:34.4.0"))
    implementation("com.google.firebase:firebase-messaging")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
