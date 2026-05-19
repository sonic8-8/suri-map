plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services) apply false
}

fun String.quotedBuildConfig(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val debugBootstrapPolicePhoneId = providers
    .gradleProperty("suriMapDebugBootstrapPolicePhoneId")
    .orElse("00000000-0000-0000-0000-000000000101")
    .get()
val debugApiBaseUrl = providers
    .gradleProperty("suriMapDebugApiBaseUrl")
    .orElse(providers.gradleProperty("suriMapApiBaseUrl"))
    .orElse("http://10.0.2.2:8080")
    .get()
val keycloakIssuerUrl = providers
    .gradleProperty("suriMapKeycloakIssuerUrl")
    .orElse("https://k14c106.p.ssafy.io/keycloak/realms/suri-map")
    .get()
val keycloakClientId = providers
    .gradleProperty("suriMapKeycloakClientId")
    .orElse("suri-map-android")
    .get()
val suriMapVersionCode = providers
    .gradleProperty("suriMapVersionCode")
    .map(String::toInt)
    .orElse(1)
    .get()
val suriMapVersionName = providers
    .gradleProperty("suriMapVersionName")
    .orElse("0.1.0")
    .get()
val debugMapOnly = providers
    .gradleProperty("suriMapDebugMapOnly")
    .orElse("false")
    .get()
    .equals("true", ignoreCase = true)
    .toString()
val debugShowcase = providers
    .gradleProperty("suriMapDebugShowcase")
    .orElse("false")
    .get()
    .equals("true", ignoreCase = true)
    .toString()
val debugMapOnlyAccessToken = providers
    .gradleProperty("suriMapDebugMapOnlyAccessToken")
    .orElse("")
    .get()
val debugMapOnlyPolicePhoneId = providers
    .gradleProperty("suriMapDebugMapOnlyPolicePhoneId")
    .orElse("")
    .get()
val debugMapOnlyIncidentId = providers
    .gradleProperty("suriMapDebugMapOnlyIncidentId")
    .orElse("")
    .get()
val debugMapOnlyOpId = providers
    .gradleProperty("suriMapDebugMapOnlyOpId")
    .orElse("")
    .get()
val debugMapOnlyDutyShiftId = providers
    .gradleProperty("suriMapDebugMapOnlyDutyShiftId")
    .orElse("")
    .get()
val debugCurrentLocationLon = providers
    .gradleProperty("suriMapDebugCurrentLocationLon")
    .orElse("")
    .get()
val debugCurrentLocationLat = providers
    .gradleProperty("suriMapDebugCurrentLocationLat")
    .orElse("")
    .get()
val debugCurrentLocationBearingDegrees = providers
    .gradleProperty("suriMapDebugCurrentLocationBearingDegrees")
    .orElse("")
    .get()
val hasGoogleServicesJson = layout.projectDirectory.file("google-services.json").asFile.exists()

if (hasGoogleServicesJson) {
    apply(plugin = "com.google.gms.google-services")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

android {
    namespace = "com.surimap"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.surimap"
        minSdk = 31
        targetSdk = 34
        versionCode = suriMapVersionCode
        versionName = suriMapVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appAuthRedirectScheme"] = "com.surimap"

        val suriMapApiBaseUrl = providers
            .gradleProperty("suriMapApiBaseUrl")
            .orElse("http://10.0.2.2:8080")
            .get()
        buildConfigField("String", "SURI_MAP_API_BASE_URL", "\"$suriMapApiBaseUrl\"")
        buildConfigField("String", "SURI_MAP_KEYCLOAK_ISSUER_URL", keycloakIssuerUrl.quotedBuildConfig())
        buildConfigField("String", "SURI_MAP_KEYCLOAK_CLIENT_ID", "\"suri-map-android\"")
        buildConfigField("String", "SURI_MAP_KEYCLOAK_REDIRECT_URI", "\"com.surimap://auth/callback\"")
        buildConfigField("boolean", "SURI_MAP_FIREBASE_MESSAGING_ENABLED", hasGoogleServicesJson.toString())

        buildConfigField("String", "SURI_MAP_DEBUG_BOOTSTRAP_POLICE_PHONE_ID", "\"\"")
        buildConfigField("boolean", "SURI_MAP_DEBUG_SHOWCASE", "false")
        buildConfigField("boolean", "SURI_MAP_DEBUG_MAP_ONLY", "false")
        buildConfigField("String", "SURI_MAP_DEBUG_MAP_ONLY_ACCESS_TOKEN", "\"\"")
        buildConfigField("String", "SURI_MAP_DEBUG_MAP_ONLY_POLICE_PHONE_ID", "\"\"")
        buildConfigField("String", "SURI_MAP_DEBUG_MAP_ONLY_INCIDENT_ID", "\"\"")
        buildConfigField("String", "SURI_MAP_DEBUG_MAP_ONLY_OP_ID", "\"\"")
        buildConfigField("String", "SURI_MAP_DEBUG_MAP_ONLY_DUTY_SHIFT_ID", "\"\"")
        buildConfigField("String", "SURI_MAP_DEBUG_CURRENT_LOCATION_LON", "\"\"")
        buildConfigField("String", "SURI_MAP_DEBUG_CURRENT_LOCATION_LAT", "\"\"")
        buildConfigField("String", "SURI_MAP_DEBUG_CURRENT_LOCATION_BEARING_DEGREES", "\"\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "SURI_MAP_API_BASE_URL", debugApiBaseUrl.quotedBuildConfig())
            buildConfigField("String", "SURI_MAP_KEYCLOAK_ISSUER_URL", keycloakIssuerUrl.quotedBuildConfig())
            buildConfigField("String", "SURI_MAP_KEYCLOAK_CLIENT_ID", keycloakClientId.quotedBuildConfig())
            buildConfigField("String", "SURI_MAP_KEYCLOAK_REDIRECT_URI", "\"com.surimap://auth/callback\"")
            buildConfigField("String", "SURI_MAP_DEBUG_BOOTSTRAP_POLICE_PHONE_ID", debugBootstrapPolicePhoneId.quotedBuildConfig())
            buildConfigField("boolean", "SURI_MAP_DEBUG_SHOWCASE", debugShowcase)
            buildConfigField("boolean", "SURI_MAP_DEBUG_MAP_ONLY", debugMapOnly)
            buildConfigField("String", "SURI_MAP_DEBUG_MAP_ONLY_ACCESS_TOKEN", debugMapOnlyAccessToken.quotedBuildConfig())
            buildConfigField("String", "SURI_MAP_DEBUG_MAP_ONLY_POLICE_PHONE_ID", debugMapOnlyPolicePhoneId.quotedBuildConfig())
            buildConfigField("String", "SURI_MAP_DEBUG_MAP_ONLY_INCIDENT_ID", debugMapOnlyIncidentId.quotedBuildConfig())
            buildConfigField("String", "SURI_MAP_DEBUG_MAP_ONLY_OP_ID", debugMapOnlyOpId.quotedBuildConfig())
            buildConfigField("String", "SURI_MAP_DEBUG_MAP_ONLY_DUTY_SHIFT_ID", debugMapOnlyDutyShiftId.quotedBuildConfig())
            buildConfigField("String", "SURI_MAP_DEBUG_CURRENT_LOCATION_LON", debugCurrentLocationLon.quotedBuildConfig())
            buildConfigField("String", "SURI_MAP_DEBUG_CURRENT_LOCATION_LAT", debugCurrentLocationLat.quotedBuildConfig())
            buildConfigField("String", "SURI_MAP_DEBUG_CURRENT_LOCATION_BEARING_DEGREES", debugCurrentLocationBearingDegrees.quotedBuildConfig())
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
        getByName("test").resources.srcDir("$rootDir/../docs/spec/fixtures")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.maplibre.android)
    implementation(libs.okhttp)
    implementation(libs.appauth)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
}
