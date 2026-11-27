import javax.inject.Inject

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
}

/** Copies part of the web app's www/ into generated assets (single source until the web app is retired). */
abstract class WwwAssets @Inject constructor(private val fs: FileSystemOperations) : DefaultTask() {
    @get:InputFiles abstract val files: ConfigurableFileCollection
    @get:Internal abstract val www: DirectoryProperty
    @get:Input abstract val includes: ListProperty<String>
    @get:OutputDirectory abstract val out: DirectoryProperty
    @TaskAction fun run() { val t = this; fs.sync { from(t.www) { include(t.includes.get()) }; into(t.out) } }
}

fun wwwAssets(name: String, vararg patterns: String) = tasks.register<WwwAssets>(name) {
    val root = rootProject.layout.projectDirectory.dir("../www")
    www.set(root)
    includes.set(patterns.toList())
    files.from(root.asFileTree.matching { include(*patterns) })
    out.set(layout.buildDirectory.dir("generated/$name"))
}
val catalogAssets = wwwAssets("catalogAssets", "data/exercises.js", "images/**")
// Exercise videos are bundled in prod (owner re-enabled them 2026-11-27); -Pform.videos=false leaves them out.
val withVideos = providers.gradleProperty("form.videos").orNull != "false"
val videoAssets = wwwAssets("videoAssets", "videos/**")

androidComponents {
    onVariants { v ->
        v.sources.assets?.addGeneratedSourceDirectory(catalogAssets, WwwAssets::out)
        if (withVideos && v.flavorName == "prod") v.sources.assets?.addGeneratedSourceDirectory(videoAssets, WwwAssets::out)
    }
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
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-gif:3.6.3")
    implementation("androidx.health.connect:connect-client:1.1.0")
    // Pin a current Fragment: ActivityResult APIs need >= 1.3 and some libraries pull an old one transitively.
    implementation("androidx.fragment:fragment:1.8.9")
    // NotificationCompat Live Updates API (setRequestPromotedOngoing, setShortCriticalText).
    implementation("androidx.core:core-ktx:1.19.1")
    // Home-screen widget (responsive sizes, Material 3 / dynamic colours).
    implementation("androidx.glance:glance-appwidget:1.2.0")
    implementation("androidx.glance:glance-material3:1.2.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
