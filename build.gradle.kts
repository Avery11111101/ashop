plugins {
    java
}

group = "com.avery"
version = "1.8.0-beta.8"

layout.buildDirectory.set(file("${System.getProperty("user.home")}/.gradle_ashop_build"))

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "jitpack"
        url = uri("https://jitpack.io")
    }
    maven {
        name = "scarsz"
        url = uri("https://nexus.scarsz.me/content/groups/public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("com.discordsrv:discordsrv:1.29.0")
    implementation("net.dv8tion:JDA:5.0.0-beta.24") {
        exclude(group = "club.minnced", module = "opus-java")
    }
    testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
}

tasks.test {
    enabled = false
}

val verifyCategories = tasks.register<JavaExec>("verifyCategories") {
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.avery.shop.catalog.CategoryVerification")
}

val testMigration = tasks.register<JavaExec>("testMigration") {
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.avery.shop.config.ConfigMigrationTest")
}

val testUpdate = tasks.register<JavaExec>("testUpdate") {
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.avery.shop.update.UpdateServiceTest")
}

val testPriceModel = tasks.register<JavaExec>("testPriceModel") {
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.avery.shop.catalog.SurvivalPriceModelTest")
}

tasks.check {
    dependsOn(verifyCategories, testMigration, testUpdate, testPriceModel)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.isIncremental = false
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveFileName.set("ashop-${version}.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) }
    }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
    doLast {
        try {
            val rootBuildLibs = file("build/libs")
            rootBuildLibs.mkdirs()
            archiveFile.get().asFile.copyTo(File(rootBuildLibs, archiveFileName.get()), overwrite = true)
        } catch (e: Exception) {
            println("Notice: Could not mirror jar to project build/libs: ${e.message}")
        }
    }
}
