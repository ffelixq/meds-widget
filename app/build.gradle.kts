import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ktlint)
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

val signingProperties = Properties()
val signingPropertiesFile = rootProject.file("keystore.properties")
if (signingPropertiesFile.exists()) {
    signingPropertiesFile.inputStream().use(signingProperties::load)
}

fun signingValue(
    environmentName: String,
    propertyName: String,
): String? =
    providers.environmentVariable(environmentName).orNull
        ?: signingProperties.getProperty(propertyName)

val buildVersionCode =
    providers
        .environmentVariable("MEDS_VERSION_CODE")
        .orNull
        ?.toIntOrNull()
        ?.takeIf { it > 0 }
        ?: 1
val releaseStoreFile = signingValue("MEDS_KEYSTORE_PATH", "storeFile")
val releaseStorePassword = signingValue("MEDS_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("MEDS_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("MEDS_KEY_PASSWORD", "keyPassword")
val hasReleaseSigning =
    listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }

android {
    namespace = "io.github.ffelixq.medswidget"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.ffelixq.medswidget"
        minSdk = 26
        targetSdk = 37
        versionCode = buildVersionCode
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField("boolean", "FIREBASE_CONFIGURED", file("google-services.json").exists().toString())
    }

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        animationsDisabled = true
    }

    packaging {
        resources.excludes +=
            setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
            )
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = true
        disable += setOf("GradleDependency", "NewerVersionAvailable")
    }
}

kotlin {
    jvmToolchain(17)
}

ktlint {
    version.set("1.8.0")
    android.set(true)
    outputToConsole.set(true)
    ignoreFailures.set(false)
    filter {
        exclude("**/generated/**")
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.id)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit4)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.glance.testing)
    testImplementation(libs.androidx.glance.appwidget.testing)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
