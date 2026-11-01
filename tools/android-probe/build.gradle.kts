plugins {
    id("com.android.application") version "8.13.2"
    id("org.jetbrains.kotlin.android") version "2.3.0"
}
android {
    namespace="com.nuviokino.probe"
    compileSdk=36
    defaultConfig { applicationId="com.nuviokino.probe"; minSdk=24; targetSdk=36; versionCode=1; versionName="1.0" }
    sourceSets.getByName("main") {
        java.srcDirs("src", "../../shared/common", "../../shared/jvm")
        resources.srcDir("../../shared/resources")
        manifest.srcFile("AndroidManifest.xml")
    }
    compileOptions { sourceCompatibility=JavaVersion.VERSION_11; targetCompatibility=JavaVersion.VERSION_11; isCoreLibraryDesugaringEnabled=true }
    packaging { resources.excludes.addAll(listOf("META-INF/versions/**","META-INF/DEPENDENCIES","META-INF/LICENSE*","META-INF/NOTICE*")) }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }
dependencies {
    implementation(files("../../platforms/android-tv/app/libs/quickjs-kt-android-1.0.5-nuvio.aar"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("org.bouncycastle:bcprov-jdk18on:1.83")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
