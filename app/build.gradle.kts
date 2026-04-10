plugins {
    id("com.android.library") version "8.3.1"
    kotlin("android") version "1.9.21"
}

val artifactBaseName = "mozc-wrapper"
val artifactVersion = rootProject.version.toString()

android {
    namespace = "com.niyarin.mozc"
    compileSdk = 34

    defaultConfig {
        minSdk = 29
        targetSdk = 34
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    packaging {
        resources {
            pickFirsts += "META-INF/proguard/androidx-*.pro"
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("com.google.protobuf:protobuf-javalite:3.21.0")
}

tasks.register<Copy>("packageVersionedReleaseAar") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.file("outputs/aar/app-release.aar"))
    into(layout.buildDirectory.dir("outputs/aar"))
    rename { "$artifactBaseName-$artifactVersion.aar" }
}
