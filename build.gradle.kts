plugins {
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
    id("org.jetbrains.compose") version "1.13.0-beta01"
}
kotlin { jvmToolchain(21) }
sourceSets {
    main { kotlin.srcDirs("shared/common", "shared/jvm", "shared/ui", "tools/workbench"); resources.srcDir("shared/resources") }
    test { kotlin.srcDir("shared/test"); resources.srcDir("shared/test-resources") }
}
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.github.dokar3:quickjs-kt:1.0.15")
    implementation("org.bouncycastle:bcprov-jdk18on:1.83")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.13.0-beta01")
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("net.java.dev.jna:jna:5.19.1")
}
tasks.test { testLogging { events("passed", "skipped", "failed") } }
compose.desktop { application { mainClass = "com.nuviokino.workbench.MainKt" } }
