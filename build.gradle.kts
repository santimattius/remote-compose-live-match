plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

subprojects {
    tasks.withType<Test>().configureEach {
        reports.html.required.set(false)
    }
}
