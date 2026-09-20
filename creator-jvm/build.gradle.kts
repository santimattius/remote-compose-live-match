plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":contract"))
    implementation(libs.remote.core)
    implementation(libs.remote.creation.core)
    implementation(libs.remote.creation.jvm)
    testImplementation(kotlin("test"))
}

fun JavaExec.fixtureArgs(check: Boolean) {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("example.livematch.creator.ScheduledFixtureGenerator")
    args(rootProject.file("fixtures").absolutePath)
    if (check) args("--check")
}

tasks.register<JavaExec>("generateScheduledFixture") {
    fixtureArgs(check = false)
}

tasks.register<JavaExec>("checkScheduledFixture") {
    fixtureArgs(check = true)
}

fun JavaExec.diagnosticFixtureArgs(check: Boolean) {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("example.livematch.creator.DiagnosticFixtureGenerator")
    args(rootProject.file("fixtures").absolutePath)
    if (check) args("--check")
}

tasks.register<JavaExec>("generateDiagnosticFixture") {
    diagnosticFixtureArgs(check = false)
}

tasks.register<JavaExec>("checkDiagnosticFixture") {
    diagnosticFixtureArgs(check = true)
}

fun JavaExec.liveFixtureArgs(check: Boolean) {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("example.livematch.creator.LiveFixtureGenerator")
    args(rootProject.file("fixtures").absolutePath)
    if (check) args("--check")
}

tasks.register<JavaExec>("generateLiveFixture") {
    liveFixtureArgs(check = false)
}

tasks.register<JavaExec>("checkLiveFixture") {
    liveFixtureArgs(check = true)
}

fun JavaExec.finishedFixtureArgs(check: Boolean) {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("example.livematch.creator.FinishedFixtureGenerator")
    args(rootProject.file("fixtures").absolutePath)
    if (check) args("--check")
}

tasks.register<JavaExec>("generateFinishedFixture") {
    finishedFixtureArgs(check = false)
}

tasks.register<JavaExec>("checkFinishedFixture") {
    finishedFixtureArgs(check = true)
}
