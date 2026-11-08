import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val isStandalone = (rootProject.name != "MyAioApp")

plugins {
  alias(libs.plugins.androidLibrary)
  id("org.jetbrains.kotlin.android")
  alias(libs.plugins.composeCompiler)
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_11)
  }
}

android {
  namespace = "com.linroid.ketch.app.android"
  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
    minSdk = libs.versions.android.minSdk.get().toInt()
    val ketchVersionName = providers.gradleProperty("VERSION_NAME").orElse("0.7.2").get()
    val ketchVersionCode = providers.gradleProperty("versionCode").orElse("1").get()
    buildConfigField("String", "VERSION_NAME", "\"$ketchVersionName\"")
    buildConfigField("int", "VERSION_CODE", ketchVersionCode)
    buildConfigField("String", "APPLICATION_ID", "\"app.myaioapp\"")
  }

  flavorDimensions += "distribution"
  productFlavors {
    create("direct") { dimension = "distribution" }
    create("play") { dimension = "distribution" }
  }

  // The release key comes from the ANDROID_KEYSTORE_* variables (CI) or, on a developer's
  // machine, from ketch.signing.* in the untracked local.properties. Debug builds use it too, so
  // they install over release builds; without it they keep Android's debug key.
  val localProperties = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
      .asText.orNull?.let { load(it.reader()) }
  }
  fun signing(env: String, local: String): String? =
    providers.environmentVariable(env).orNull ?: localProperties.getProperty("ketch.signing.$local")

  signingConfigs {
    val keystoreFile = signing("ANDROID_KEYSTORE_FILE", "storeFile")
    if (keystoreFile != null) {
      create("release") {
        storeFile = rootProject.file(keystoreFile)
        storePassword = signing("ANDROID_KEYSTORE_PASSWORD", "storePassword")
        keyAlias = signing("ANDROID_KEY_ALIAS", "keyAlias")
        keyPassword = signing("ANDROID_KEY_PASSWORD", "keyPassword")
      }
    }
  }
  buildTypes {
    debug {
      signingConfigs.findByName("release")?.let {
        signingConfig = it
      }
    }
    release {
      isMinifyEnabled = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        projectDir.resolve("proguard-rules.pro"),
      )
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  packaging {
    resources {
      // kotlin/**/*.kotlin_builtins are read only by kotlin-reflect, which the app leaves out.
      excludes += setOf(
        "META-INF/DEPENDENCIES",
        "META-INF/{INDEX.LIST,io.netty.versions.properties}",
        "META-INF/*.version",
        "META-INF/native-image/**",
        "META-INF/version-control-info.textproto",
        "META-INF/com/android/build/gradle/app-metadata.properties",
        "META-INF/androidx/**",
        "META-INF/**/*.properties",
        "kotlin/**",
        "kotlinx-schema.properties",
        "DebugProbesKt.bin",
        "org/fusesource/**",
      )
    }
  }
}

// Releases take the workflow's run number as their version code. In MyAioApp this is built as a library.

// Koog, kotlinx-schema and Ktor's server depend on kotlin-reflect for features the app does not
// use (reflective tool sets and schemas, loading server modules by name). Its R8 rules keep most
// of it, so release builds leave it out.
configurations.matching { it.name.endsWith("ReleaseRuntimeClasspath") }.configureEach {
  exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")
}

dependencies {
  testImplementation(libs.kotlin.testJunit)
  testImplementation(libs.kotlinx.coroutines.test)
  implementation(projects.config)
  implementation(projects.app.shared)
  implementation(projects.library.core)
  implementation(projects.library.ktor)
  implementation(projects.library.hls)
  implementation(projects.library.dash)
  implementation(projects.library.metalink)
  implementation(projects.library.webdav)
  implementation(projects.library.torrent)
  implementation(projects.library.sqlite)
  implementation(libs.androidx.activity.compose)
  debugImplementation(libs.compose.uiTooling)
}
