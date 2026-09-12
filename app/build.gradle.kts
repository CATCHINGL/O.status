plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace="com.catch7ng.ostatus"
 compileSdk=35
 defaultConfig {
  applicationId="com.catch7ng.ostatus"
  minSdk=29
  targetSdk=35
  versionCode=34
  versionName="1.0.0"
 }
 compileOptions {
  sourceCompatibility=JavaVersion.VERSION_17
  targetCompatibility=JavaVersion.VERSION_17
 }
 kotlinOptions { jvmTarget="17" }
 lint {
  checkReleaseBuilds = false
 }
}
dependencies {
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("androidx.appcompat:appcompat:1.7.0")
}
