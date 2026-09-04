import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val parallelKeyStorePath = providers.environmentVariable("FINANZCOCKPIT_PARALLEL_KEYSTORE").orNull
val parallelStorePassword = providers.environmentVariable("FINANZCOCKPIT_PARALLEL_STORE_PASSWORD").orNull
val parallelKeyAlias = providers.environmentVariable("FINANZCOCKPIT_PARALLEL_KEY_ALIAS").orNull
val parallelKeyPassword = providers.environmentVariable("FINANZCOCKPIT_PARALLEL_KEY_PASSWORD").orNull
val updateKeyStorePath = providers.environmentVariable("FINANZCOCKPIT_UPDATE_KEYSTORE").orNull
val updateStorePassword = providers.environmentVariable("FINANZCOCKPIT_UPDATE_STORE_PASSWORD").orNull
val updateKeyAlias = providers.environmentVariable("FINANZCOCKPIT_UPDATE_KEY_ALIAS").orNull
val updateKeyPassword = providers.environmentVariable("FINANZCOCKPIT_UPDATE_KEY_PASSWORD").orNull

android {
    namespace = "de.finanzcockpit.app"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "de.finanzcockpit.mobile"
        minSdk = 30
        targetSdk = 37
        versionCode = 2
        versionName = "2.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    val parallelSigning = if (
        parallelKeyStorePath != null && parallelStorePassword != null &&
        parallelKeyAlias != null && parallelKeyPassword != null
    ) {
        signingConfigs.create("parallelTest") {
            storeFile = file(parallelKeyStorePath)
            storePassword = parallelStorePassword
            keyAlias = parallelKeyAlias
            keyPassword = parallelKeyPassword
        }
    } else {
        null
    }
    val updateSigning = if (
        updateKeyStorePath != null && updateStorePassword != null &&
        updateKeyAlias != null && updateKeyPassword != null
    ) {
        signingConfigs.create("updateRelease") {
            storeFile = file(updateKeyStorePath)
            storePassword = updateStorePassword
            keyAlias = updateKeyAlias
            keyPassword = updateKeyPassword
        }
    } else {
        null
    }
    if (gradle.startParameter.taskNames.any { it.contains("ParallelTest", ignoreCase = true) }) {
        check(parallelSigning != null) {
            "Die vier FINANZCOCKPIT_PARALLEL_* Umgebungsvariablen für die Testsignatur fehlen."
        }
    }
    if (gradle.startParameter.taskNames.any {
            it.contains("assembleRelease", ignoreCase = true) ||
                it.contains("bundleRelease", ignoreCase = true)
        }
    ) {
        check(updateSigning != null) {
            "Die vier FINANZCOCKPIT_UPDATE_* Umgebungsvariablen für die dauerhafte Releasesignatur fehlen."
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            updateSigning?.let { signingConfig = it }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("parallelTest") {
            initWith(getByName("release"))
            applicationIdSuffix = ".test"
            versionNameSuffix = "-parallel"
            isDebuggable = false
            matchingFallbacks += "release"
            parallelSigning?.let { signingConfig = it }
        }
    }

    sourceSets {
        // Die bekannte HTML-Ausgangsbasis gehört nur zur neuen Installationslinie.
        getByName("release").assets.directories.add("src/parallelTest/assets")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("androidx.fragment:fragment-ktx:1.9.0")
    implementation("androidx.biometric:biometric:1.1.0")

    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.sqlite:sqlite:2.7.0")
    implementation("net.zetetic:sqlcipher-android:4.18.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("org.json:json:20260814")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
