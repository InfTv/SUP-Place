plugins {
    id("com.android.application")
}

val releaseKeystore = providers.environmentVariable("SUP_PLACE_KEYSTORE").orNull
val releaseStorePassword = providers.environmentVariable("SUP_PLACE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("SUP_PLACE_KEY_ALIAS").orNull ?: "supplace"
val releaseKeyPassword = providers.environmentVariable("SUP_PLACE_KEY_PASSWORD").orNull
    ?: releaseStorePassword

android {
    namespace = "com.supplace.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.supplace.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 31
        versionName = "0.4.22"
    }

    signingConfigs {
        create("release") {
            if (releaseKeystore != null && releaseStorePassword != null) {
                storeFile = file(releaseKeystore)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                storeType = "pkcs12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            vcsInfo.include = false
            if (releaseKeystore != null && releaseStorePassword != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
