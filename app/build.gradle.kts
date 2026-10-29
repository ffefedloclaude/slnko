plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Číslo buildu z GitHub Actions (každé zostavenie má vyššiu verziu, takže sa dá aktualizovať).
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "sk.slnko.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "sk.slnko.app"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    signingConfigs {
        create("slnko") {
            storeFile = rootProject.file("keystore/slnko.keystore")
            storeType = "pkcs12"
            storePassword = "android"
            keyAlias = "slnko"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("slnko")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("slnko")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}
