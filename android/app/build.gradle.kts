plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

fun String.quotedBuildConfig(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val fixtureAccountCode = providers
    .gradleProperty("suriMapFixtureAccountCode")
    .orElse("")
    .get()
val fixturePassword = providers
    .gradleProperty("suriMapFixturePassword")
    .orElse("")
    .get()
val fixturePolicePhoneCode = providers
    .gradleProperty("suriMapFixturePolicePhoneCode")
    .orElse("")
    .get()

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

        buildConfigField("String", "SURI_MAP_FIXTURE_ACCOUNT_CODE", "\"\"")
        buildConfigField("String", "SURI_MAP_FIXTURE_PASSWORD", "\"\"")
        buildConfigField("String", "SURI_MAP_FIXTURE_POLICE_PHONE_CODE", "\"\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "SURI_MAP_FIXTURE_ACCOUNT_CODE", fixtureAccountCode.quotedBuildConfig())
            buildConfigField("String", "SURI_MAP_FIXTURE_PASSWORD", fixturePassword.quotedBuildConfig())
            buildConfigField("String", "SURI_MAP_FIXTURE_POLICE_PHONE_CODE", fixturePolicePhoneCode.quotedBuildConfig())
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
    implementation(libs.maplibre.android)
    implementation(libs.okhttp)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
}
