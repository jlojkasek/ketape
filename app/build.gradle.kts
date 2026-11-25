plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "io.github.jlojkasek.ketape"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "io.github.jlojkasek.ketape"
        minSdk = 23
        targetSdk = 34
        versionCode = 17
        versionName = "1.1.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { buildConfig = true }
    signingConfigs {
        create("ketape") {
            // Preserve the published certificate; keep the key outside version control.
            storeFile = rootProject.file(".signing/release.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        release {
            // CI can test a disposable signature; release.py rejects it for distribution.
            signingConfig = signingConfigs.getByName(
                if (rootProject.file(".signing/release.keystore").exists()) "ketape" else "debug"
            )
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    implementation(project(":karoo-ext"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
