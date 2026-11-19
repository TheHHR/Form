plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Native app versions from native/version.properties (>= 4.0.0, above the web app's 3.x).
val appVersionName = rootProject.file("version.properties").readText().trim()
val (vMajor, vMinor, vPatch) = appVersionName.split(".").map { it.toInt() }
// CI passes the run number so each beta build upgrades the previous one.
val buildOffset = (System.getenv("FORM_BUILD_NUMBER") ?: "0").toInt()

android {
    namespace = "com.thehhr.form"
    compileSdk { version = release(37) { minorApiLevel = 2 } }
    defaultConfig {
        applicationId = "com.thehhr.form"
        minSdk = 29
        targetSdk = 36
        versionCode = vMajor * 10000 + vMinor * 100 + vPatch + buildOffset
        versionName = appVersionName
    }
    flavorDimensions += "variant"
    productFlavors {
        create("prod") {
            dimension = "variant"
            applicationId = "com.thehhr.form"
            resValue("string", "app_name", "Form")
        }
        create("beta") {
            dimension = "variant"
            applicationId = "com.thehhr.form.beta"
            versionNameSuffix = "-beta"
            resValue("string", "app_name", "Form Beta")
        }
    }
    signingConfigs {
        create("release") {
            val ks = System.getenv("FORM_KEYSTORE")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("FORM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FORM_KEY_ALIAS")
                keyPassword = System.getenv("FORM_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (System.getenv("FORM_KEYSTORE") != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures { compose = true; resValues = true; buildConfig = true }
    sourceSets {
        getByName("main").assets.srcDir("build/generated/formAssets/main")
        getByName("prod").assets.srcDir("build/generated/formAssets/prod")
    }
}


// Exercise catalogue + stills come from the web app's www/ (single source until the web app is retired).
val copyCatalog by tasks.registering(Copy::class) {
    from("../../www") { include("data/exercises.js", "images/**") }
    into(layout.buildDirectory.dir("generated/formAssets/main"))
}
// Exercise videos ship in prod only; the beta APK stays small.
val copyVideos by tasks.registering(Copy::class) {
    from("../../www") { include("videos/**") }
    into(layout.buildDirectory.dir("generated/formAssets/prod"))
}
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(copyCatalog)
    if (name.contains("Prod")) dependsOn(copyVideos)
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2026.11.01")
    implementation(bom)
    implementation("androidx.compose.material3:material3:1.5.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.1")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-gif:3.6.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
