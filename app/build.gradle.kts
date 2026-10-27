import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.vtoppp.vtop"
    compileSdk = 37

    val localProps = Properties().apply {
        val propsFile = rootProject.file("local.properties")
        if (propsFile.exists()) propsFile.inputStream().use(::load)
    }
    fun signingValue(localKey: String, envKey: String): String? =
        localProps.getProperty(localKey)?.trim()?.takeIf { it.isNotEmpty() }
            ?: System.getenv(envKey)?.trim()?.takeIf { it.isNotEmpty() }

    val signingStoreFile = signingValue("vtop.signing.storeFile", "VTOP_STORE_FILE")
        ?.let { rootProject.file(it) }
        ?: rootProject.file("local/keystores/vtop-release.keystore")
    val signingStorePassword = signingValue("vtop.signing.storePassword", "VTOP_STORE_PASSWORD")
    val signingKeyAlias = signingValue("vtop.signing.keyAlias", "VTOP_KEY_ALIAS")
    val signingKeyPassword = signingValue("vtop.signing.keyPassword", "VTOP_KEY_PASSWORD")

    signingConfigs {
        create("release") {
            storeFile = signingStoreFile
            if (signingStorePassword != null && signingKeyAlias != null) {
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword ?: signingStorePassword
            }
        }
    }

    defaultConfig {
        applicationId = "dev.vtoppp.vtop"
        minSdk = 26
        targetSdk = 37
            versionCode = 7
            versionName = "0.1.6"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            val releaseSigning = signingConfigs.getByName("release")
            val signingReady = releaseSigning.storePassword != null &&
                releaseSigning.keyAlias != null &&
                releaseSigning.storeFile?.exists() == true
            if (signingReady) {
                signingConfig = releaseSigning
            } else {
                logger.warn(
                    "VTOP: release build will be UNSIGNED. Configure signing via local.properties " +
                        "(vtop.signing.storeFile, vtop.signing.storePassword, vtop.signing.keyAlias, " +
                        "optional vtop.signing.keyPassword) or environment variables " +
                        "(VTOP_STORE_FILE, VTOP_STORE_PASSWORD, VTOP_KEY_ALIAS, VTOP_KEY_PASSWORD). " +
                        "See README.md, section 'Release signing'."
                )
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)
    implementation(libs.okhttp)
    implementation(libs.okhttp.doh)
    implementation(libs.jsoup)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
