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

fun JavaExec.fixtureArgs(
    mainClassName: String,
    check: Boolean,
) {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(mainClassName)
    args(rootProject.file("fixtures").absolutePath)
    if (check) args("--check")
}

fun registerFixtureTasks(
    name: String,
    mainClassName: String,
) {
    tasks.register<JavaExec>("generate${name}Fixture") {
        fixtureArgs(mainClassName, check = false)
    }
    tasks.register<JavaExec>("check${name}Fixture") {
        fixtureArgs(mainClassName, check = true)
    }
}

registerFixtureTasks("Scheduled", "example.livematch.creator.ScheduledFixtureGenerator")
registerFixtureTasks("Live", "example.livematch.creator.LiveFixtureGenerator")
registerFixtureTasks("Finished", "example.livematch.creator.FinishedFixtureGenerator")
