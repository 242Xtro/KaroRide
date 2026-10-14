import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val signingValues = Properties().apply {
    val source = rootProject.file("secrets/signing.properties")
    if (source.isFile) source.inputStream().use { load(it) }
}
android {
    namespace = "de.markus.aceride"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "de.markus.aceride"
        minSdk = 31 // This project targets Karoo 3; no location permission for older Android.
        targetSdk = 34
        versionCode = 9
        versionName = "1.2.0-preview.1"
    }
    buildFeatures { buildConfig = true }
    // Both languages stay in the package, including future bundles; switching works offline.
    bundle { language { enableSplit = false } }
    // Private sideload build: omit encrypted Play dependency metadata, which varies
    // between builds despite identical code/resources. Runtime notices are in assets.
    dependenciesInfo { includeInApk = false; includeInBundle = false }
    packaging {
        resources.merges += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/AL2.0", "META-INF/LGPL2.1")
        jniLibs.keepDebugSymbols += "**/*.so" // Preserve supplied binaries; no NDK installation.
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    // Never use Android's global ~/.android/debug.keystore or an upstream key.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("secrets/aceride.jks")
            storePassword = signingValues.getProperty("storePassword", "")
            keyAlias = signingValues.getProperty("keyAlias", "aceride")
            keyPassword = signingValues.getProperty("keyPassword", "")
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("debug") }
        release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("debug") }
    }
    flavorDimensions += "backend"
    productFlavors {
        create("preparation") { dimension = "backend"; buildConfigField("boolean", "HARDWARE", "false") }
        create("hardware") {
            dimension = "backend"; buildConfigField("boolean", "HARDWARE", "true")
            ndk { abiFilters += "arm64-v8a" }
        }
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":core"))
    implementation(project(":karoo-ext"))
    add("hardwareImplementation", "com.arashivision.sdk:sdk-camera:2.1.5")
    // The SDK's KMP metadata can introduce this node late during artifact resolution.
    // Declare its exact aligned version so normal/offline resolution also locks it.
    add("hardwareImplementation", "org.jetbrains.kotlin:kotlin-stdlib-common:2.3.20")
}
tasks.configureEach {
    if (name.startsWith("validateSigning")) {
        doFirst {
            check(rootProject.file("secrets/aceride.jks").isFile && signingValues.getProperty("storePassword") != null) {
                "Eigener lokaler Signierungsschlüssel fehlt. Siehe docs/BUILD.md; keine Passwörter im Chat."
            }
        }
    }
    if (name.startsWith("pre") && name.contains("Hardware") && name.endsWith("Build")) {
        doFirst {
            check(file("src/hardware/kotlin/de/markus/aceride/VendorBinding.kt").isFile) {
                "Hardware-Build gesperrt: offizielles Camera-SDK V2.1.5 und geprüfte VendorBinding fehlen. Siehe docs/SDK-Zugang.md."
            }
        }
    }
}

