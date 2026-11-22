import com.google.gms.googleservices.GoogleServicesPlugin
import groovy.json.JsonOutput
import java.security.KeyStore
import java.security.MessageDigest
import java.util.HexFormat

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
}

extensions.configure<GoogleServicesPlugin.GoogleServicesPluginConfig> {
    missingGoogleServicesStrategy = GoogleServicesPlugin.MissingGoogleServicesStrategy.WARN
}

val mapsKey = providers.gradleProperty("navetteMapsAndroidKey").orElse("")
val contactEmail = providers.gradleProperty("navetteBookingEmail").orElse("")
val policyUrl = providers.gradleProperty("navettePrivacyUrl").orElse("")
val playVersionCode = providers.gradleProperty("navettePlayVersionCode").map(String::toInt).orElse(20)
val playHighestVersionCode = providers.gradleProperty("navettePlayHighestVersionCode")
val playUploadFingerprint = providers.gradleProperty("navettePlayUploadSha256")
val uploadStore = providers.environmentVariable("NAVETTE_UPLOAD_STORE")
val uploadStorePassword = providers.environmentVariable("NAVETTE_UPLOAD_STORE_PASSWORD")
val uploadAlias = providers.environmentVariable("NAVETTE_UPLOAD_ALIAS")
val uploadKeyPassword = providers.environmentVariable("NAVETTE_UPLOAD_KEY_PASSWORD")
val playFirebaseConfig = file("src/release/google-services.json")
val sharedFirebaseConfig = file("google-services.json")

android {
    namespace = "com.phoenix.navette28"
    compileSdk { version = release(37) }

    defaultConfig {
        applicationId = "com.phoenix.navette28"
        minSdk = 24
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
                storePassword = uploadStorePassword.orNull
                keyAlias = uploadAlias.orNull
                keyPassword = uploadKeyPassword.orNull
            }
        }
    }
    buildTypes {
        debug {
            buildConfigField(
                "boolean",
                "USE_DEBUG_APP_CHECK",
                providers.gradleProperty("navetteDebugAppCheck").orElse("false").get().toBoolean().toString(),
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfigs.findByName("upload")?.let { signingConfig = it }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    buildFeatures { buildConfig = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.applicationId.set("com.phoenix.navette")
        variant.outputs.forEach { it.versionCode.set(playVersionCode) }
    }
}

val verifyPlayBundle by tasks.registering {
    doLast {
        val highest = playHighestVersionCode.orNull?.toIntOrNull()
        check(highest != null && highest > 0) {
            "Renseignez navettePlayHighestVersionCode avec le plus grand code vérifié dans Play Console."
        }
        val version = playVersionCode.get()
        check(version > highest && version <= 2_100_000_000) {
            "Le code de la mise à jour doit dépasser le plus grand code déjà importé dans Play Console."
        }
        check(playFirebaseConfig.isFile || sharedFirebaseConfig.isFile) {
            "Ajoutez la configuration Firebase de com.phoenix.navette dans app/src/release/google-services.json."
        }
        val expectedFingerprint = playUploadFingerprint.orNull?.replace(":", "")?.trim()
        check(expectedFingerprint != null && expectedFingerprint.matches(Regex("[0-9a-fA-F]{64}"))) {
            "Renseignez navettePlayUploadSha256 avec l’empreinte SHA-256 du certificat d’importation de Play Console."
        }
        check(listOf(uploadStore, uploadStorePassword, uploadAlias, uploadKeyPassword).all { !it.orNull.isNullOrBlank() }) {
            "Configurez les paramètres locaux de la clé d’importation autorisée."
        }
        val store = KeyStore.getInstance(file(uploadStore.get()), uploadStorePassword.get().toCharArray())
        val certificate = store.getCertificate(uploadAlias.get())
        check(store.isKeyEntry(uploadAlias.get()) && certificate != null) {
            "La clé d’importation est introuvable dans le fichier de clés."
        }
        val fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.encoded))
        check(fingerprint.equals(expectedFingerprint, ignoreCase = true)) {
            "Le certificat de la clé locale ne correspond pas au certificat d’importation Play Console."
        }
    }
}

tasks.matching { it.name == "packageReleaseBundle" }.configureEach {
    dependsOn(verifyPlayBundle)
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.fragment:fragment-ktx:1.9.1")
    implementation("com.google.android.material:material:1.14.0")
    implementation("com.google.android.gms:play-services-maps:20.1.0")
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.18.1")
}
