plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services) apply false
}

fun String.quotedBuildConfig(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val debugBootstrapAccountCode = providers
    .gradleProperty("suriMapDebugBootstrapAccountCode")
    .orElse("acct-precinct-team")
    .get()
val debugBootstrapPassword = providers
    .gradleProperty("suriMapDebugBootstrapPassword")
    .orElse("fixture")
    .get()
val debugBootstrapPolicePhoneCode = providers
    .gradleProperty("suriMapDebugBootstrapPolicePhoneCode")
    .orElse("dev-precinct-phone-01")
    .get()
val debugApiBaseUrl = providers
    .gradleProperty("suriMapDebugApiBaseUrl")
    .orElse(providers.gradleProperty("suriMapApiBaseUrl"))
    .orElse("http://127.0.0.1:8080")
    .get()
val debugMapOnly = providers
    .gradleProperty("suriMapDebugMapOnly")
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
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val suriMapApiBaseUrl = providers
            .gradleProperty("suriMapApiBaseUrl")
            .orElse("http://10.0.2.2:8080")
            .get()
        buildConfigField("String", "SURI_MAP_API_BASE_URL", "\"$suriMapApiBaseUrl\"")
        buildConfigField("boolean", "SURI_MAP_FIREBASE_MESSAGING_ENABLED", hasGoogleServicesJson.toString())

        buildConfigField("String", "SURI_MAP_DEBUG_BOOTSTRAP_ACCOUNT_CODE", "\"\"")
        buildConfigField("String", "SURI_MAP_DEBUG_BOOTSTRAP_PASSWORD", "\"\"")
        buildConfigField("String", "SURI_MAP_DEBUG_BOOTSTRAP_POLICE_PHONE_CODE", "\"\"")
        buildConfigField("boolean", "SURI_MAP_DEBUG_MAP_ONLY", "false")
        buildConfigField("String", "SURI_MAP_DEBUG_MAP_ONLY_ACCESS_TOKEN", "\"\"")
        buildConfigField("String", "SURI_MAP_DEBUG_MAP_ONLY_POLICE_PHONE_ID", "\"\"")
        buildConfigField("String", "SURI_MAP_DEBUG_MAP_ONLY_INCIDENT_ID", "\"\"")
        buildConfigField("String", "SURI_MAP_DEBUG_MAP_ONLY_OP_ID", "\"\"")
        buildConfigField("String", "SURI_MAP_DEBUG_MAP_ONLY_DUTY_SHIFT_ID", "\"\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "SURI_MAP_API_BASE_URL", debugApiBaseUrl.quotedBuildConfig())
            buildConfigField("String", "SURI_MAP_DEBUG_BOOTSTRAP_ACCOUNT_CODE", debugBootstrapAccountCode.quotedBuildConfig())
            buildConfigField("String", "SURI_MAP_DEBUG_BOOTSTRAP_PASSWORD", debugBootstrapPassword.quotedBuildConfig())
            buildConfigField("String", "SURI_MAP_DEBUG_BOOTSTRAP_POLICE_PHONE_CODE", debugBootstrapPolicePhoneCode.quotedBuildConfig())
            buildConfigField("boolean", "SURI_MAP_DEBUG_MAP_ONLY", debugMapOnly)
            buildConfigField("String", "SURI_MAP_DEBUG_MAP_ONLY_ACCESS_TOKEN", debugMapOnlyAccessToken.quotedBuildConfig())
            buildConfigField("String", "SURI_MAP_DEBUG_MAP_ONLY_POLICE_PHONE_ID", debugMapOnlyPolicePhoneId.quotedBuildConfig())
            buildConfigField("String", "SURI_MAP_DEBUG_MAP_ONLY_INCIDENT_ID", debugMapOnlyIncidentId.quotedBuildConfig())
            buildConfigField("String", "SURI_MAP_DEBUG_MAP_ONLY_OP_ID", debugMapOnlyOpId.quotedBuildConfig())
            buildConfigField("String", "SURI_MAP_DEBUG_MAP_ONLY_DUTY_SHIFT_ID", debugMapOnlyDutyShiftId.quotedBuildConfig())
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

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
}
