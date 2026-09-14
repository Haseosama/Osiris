import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Third-party API keys for the native (no-backend) data sources — read from local.properties
// (gitignored, never committed) rather than hardcoded, same as sdk.dir already is. Missing here
// just means those BuildConfig fields come out as empty strings, not a build failure — the
// sources that will read them (Phase 3 of the "no backend" migration) treat empty as "unset".
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun localProp(key: String): String = localProperties.getProperty(key) ?: ""

android {
    namespace = "com.osiris.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.osiris.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"

        buildConfigField("String", "OPENSKY_CLIENT_ID", "\"${localProp("OPENSKY_CLIENT_ID")}\"")
        buildConfigField("String", "OPENSKY_CLIENT_SECRET", "\"${localProp("OPENSKY_CLIENT_SECRET")}\"")
        buildConfigField("String", "AIS_API_KEY", "\"${localProp("AIS_API_KEY")}\"")
        buildConfigField("String", "TOMTOM_API_KEY", "\"${localProp("TOMTOM_API_KEY")}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Native vector map rendering — direct equivalent of the MapLibre GL JS map Osiris uses
    // on the web, pulls in org.maplibre.gl:android-sdk-geojson transitively for Feature/Point.
    implementation("org.maplibre.gl:android-sdk:11.11.0")

    // REST client for the self-hosted Osiris backend (/api/*)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Backend URL + layer preferences
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Device location to center the map on launch
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // CCTV snapshot preview (static JPEGs from /api/cctv)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // MP4 playback for the handful of CCTV cameras that expose a real video stream
    // (e.g. Quebec 511) instead of a periodically-refreshed JPEG snapshot.
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    // HLS (.m3u8) playback — several native CCTV sources (Florida, Georgia, North Carolina,
    // Louisiana) serve their video feed as an HLS playlist rather than a plain MP4; the core
    // exoplayer artifact alone doesn't include the HLS extractor.
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")

    // Home screen widget — Compose-based (Glance) rather than classic RemoteViews/XML, to stay
    // consistent with the rest of the UI instead of introducing a second layout system.
    implementation("androidx.glance:glance-appwidget:1.2.0")

    // RECON "Téléphone" tool — same library the backend used server-side
    // (google-libphonenumber), now parsing on-device instead of proxying the lookup.
    implementation("com.googlecode.libphonenumber:libphonenumber:8.13.51")

    // Satellites layer — SGP4/SDP4 TLE propagation. The backend used satellite.js server-side;
    // this is a Java port of the same NORAD SGP4/SDP4 model (MIT-licensed), published to Maven
    // Central rather than needing a JitPack SHA pin.
    implementation("uk.me.g4dpz:predict4java:1.2.2")

    // RECON watchlist — periodic background re-check of saved queries (CVE/WHOIS/fuites/…) so a
    // change can notify the user without the app being open.
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
