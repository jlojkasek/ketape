plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "io.github.jlojkasek.ketape"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.github.jlojkasek.ketape"
        minSdk = 23
        targetSdk = 34
        versionCode = 10
        versionName = "1.0.0"
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
