import org.gradle.api.tasks.Sync

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val scheduledFixtureAssets = layout.buildDirectory.dir("generated/assets/scheduledFixture")
val diagnosticFixtureAssets = layout.buildDirectory.dir("generated/assets/diagnosticFixture")
val stageScheduledFixture =
    tasks.register<Sync>("stageScheduledFixture") {
        from(rootProject.layout.projectDirectory.file("fixtures/scheduled-match.rce"))
        into(scheduledFixtureAssets)
    }
val stageDiagnosticFixture =
    tasks.register<Sync>("stageDiagnosticFixture") {
        from(
            rootProject.layout.projectDirectory.file("fixtures/diagnostic/paint-probe.rc"),
            rootProject.layout.projectDirectory.file("fixtures/diagnostic/alpha18-api6-control.rc"),
            rootProject.layout.projectDirectory.file("fixtures/live-match.rce"),
            rootProject.layout.projectDirectory.file("fixtures/finished-match.rce"),
        )
        into(diagnosticFixtureAssets)
    }

tasks.named("preBuild").configure {
    dependsOn(stageScheduledFixture, stageDiagnosticFixture)
}

android {
    namespace = "example.livematch.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "example.livematch.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        getByName("main").assets.srcDir(scheduledFixtureAssets)
        getByName("debug").assets.srcDir(diagnosticFixtureAssets)
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":contract"))
    implementation(libs.tink.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.remote.core)
    implementation(libs.remote.player.view)
    testImplementation(libs.remote.creation.compose)
    testImplementation(libs.remote.creation.core)
    testImplementation(kotlin("test"))
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
