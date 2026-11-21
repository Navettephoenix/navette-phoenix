import groovy.json.JsonOutput

plugins { id("com.android.application") }

if (file("google-services.json").isFile) {
    apply(plugin = "com.google.gms.google-services")
}

val mapsKey = providers.gradleProperty("navetteMapsAndroidKey").orElse("")
val contactEmail = providers.gradleProperty("navetteBookingEmail").orElse("")
val policyUrl = providers.gradleProperty("navettePrivacyUrl").orElse("")
val uploadStore = providers.environmentVariable("NAVETTE_UPLOAD_STORE")

android {
    namespace = "com.phoenix.navette28"
    compileSdk { version = release(37) }

    defaultConfig {
        applicationId = "com.phoenix.navette28"
        minSdk = 26
        targetSdk = 37
        versionCode = 20
        versionName = "20.0"
        manifestPlaceholders["mapsAndroidKey"] = mapsKey.get()
        buildConfigField("boolean", "HAS_MAPS", mapsKey.get().isNotBlank().toString())
        buildConfigField("String", "BOOKING_EMAIL", JsonOutput.toJson(contactEmail.get()))
        buildConfigField("String", "PRIVACY_URL", JsonOutput.toJson(policyUrl.get()))
    }

    signingConfigs {
        if (uploadStore.isPresent) {
            create("upload") {
                storeFile = file(uploadStore.get())
                storePassword = providers.environmentVariable("NAVETTE_UPLOAD_STORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("NAVETTE_UPLOAD_ALIAS").orNull
                keyPassword = providers.environmentVariable("NAVETTE_UPLOAD_KEY_PASSWORD").orNull
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfigs.findByName("upload")?.let { signingConfig = it }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("com.google.android.gms:play-services-maps:20.1.0")
    implementation(platform("com.google.firebase:firebase-bom:34.18.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    testImplementation("junit:junit:4.13.2")
}
