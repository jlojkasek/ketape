plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "de.jannes.stacked"
    compileSdk = 35
    defaultConfig {
        applicationId = "de.jannes.stacked"
        minSdk = 23
        targetSdk = 34
        versionCode = 6
        versionName = "0.1.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { buildConfig = true }
    buildTypes {
        release {
            // Preserve update compatibility with the APKs already installed on Karoo.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
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
