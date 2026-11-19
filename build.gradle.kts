import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.20"
    id("org.jetbrains.intellij.platform") version "2.20.1"
    id("org.jetbrains.intellij.platform.grammarkit") version "2.20.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        val localPath = providers.gradleProperty("platformLocalPath").orNull?.takeIf { it.isNotBlank() }
        if (localPath != null) {
            local(localPath)
        } else {
            intellijIdea(providers.gradleProperty("platformVersion"))
        }
        bundledPlugin("com.intellij.modules.json")
        bundledPlugin("intellij.structureView.plugin")
        grammarKit()
        jflex()
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        apiVersion.set(KotlinVersion.KOTLIN_2_2)
    }
}

sourceSets {
    main {
        java.srcDir("src/main/gen")
    }
}

intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        ides {
            // the platform the plugin is built and tested against: platformVersion, or platformLocalPath locally
            current()
        }
    }
}

tasks {
    generateLexer {
        sourceFile.set(file("src/main/grammar/Nunjucks.flex"))
        targetRootOutputDir.set(file("src/main/gen"))
        packageName.set("uk.steers.nunjucks.lexer")
        // Without pathToClass, purgeOldFiles deletes the whole of src/main/gen, including the parser's output
        pathToClass.set("uk/steers/nunjucks/lexer/_NunjucksLexer.java")
        purgeOldFiles.set(true)
    }

    generateParser {
        sourceFile.set(file("src/main/grammar/Nunjucks.bnf"))
        targetRootOutputDir.set(file("src/main/gen"))
        pathToParser.set("uk/steers/nunjucks/parser/NunjucksParser.java")
        pathToPsiRoot.set("uk/steers/nunjucks/psi")
        purgeOldFiles.set(true)
    }

    compileKotlin {
        dependsOn(generateLexer, generateParser)
    }

    // The verifier runs in its own JVM, so pass on any custom trust store Gradle was given
    // (e.g. systemProp.javax.net.ssl.trustStore in ~/.gradle/gradle.properties for a TLS-inspecting proxy).
    verifyPlugin {
        listOf("javax.net.ssl.trustStore", "javax.net.ssl.trustStorePassword", "javax.net.ssl.trustStoreType").forEach { key ->
            providers.systemProperty(key).orNull?.let { systemProperty(key, it) }
        }
    }

    jar {
        from(files("LICENSE", "NOTICE", "LICENSE-APACHE-2.0.txt")) { into("META-INF") }
    }

    compileJava {
        dependsOn(generateLexer, generateParser)
    }

    test {
        useJUnit()
        // Optional diagnostics, e.g. -PnunjucksCorpus=/path/to/views:/path/to/node_modules/govuk-frontend/dist
        mapOf(
            "nunjucksCorpus" to "nunjucks.corpus",
            "nunjucksFormatCorpus" to "nunjucks.formatCorpus",
            "realProject" to "nunjucks.realProject",
            "dumpBlocks" to "nunjucks.dumpBlocks",
        ).forEach { (property, systemProperty) ->
            providers.gradleProperty(property).orNull?.let { systemProperty(systemProperty, it) }
        }
        testLogging { showStandardStreams = providers.gradleProperty("showTestOutput").isPresent }
    }
}
