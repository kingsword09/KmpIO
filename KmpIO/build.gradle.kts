import java.util.Properties
import java.io.FileInputStream
import org.gradle.kotlin.dsl.signing
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    id("com.android.library")
    kotlin("multiplatform")
    kotlin("native.cocoapods")
    id("maven-publish")
    id("signing")
    kotlin("plugin.atomicfu") version "1.9.10"
    id("org.jetbrains.dokka") version "1.9.0"
    id("com.github.ben-manes.versions") version "0.48.0"
}

repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
}

val localProps = Properties().apply {
    load(FileInputStream(project.rootProject.file("local.properties")))
    project.extra["signing.keyId"] = get("signing.keyId")
    project.extra["signing.password"] = get("signing.password")
    project.extra["signing.secretKeyRingFile"] = get("signing.secretKeyRingFile")
}

val mavenArtifactId = "kmp-io"
val appleFrameworkName = "KmpIO"
group = "io.github.skolson"
version = "0.1.4"

val androidMinSdk = 26
val androidCompileSdkVersion = 34
val iosMinSdk = "14"
val kmpPackageName = "com.oldguy.common.io"

val androidMainDirectory = projectDir.resolve("src").resolve("androidMain")
val javadocTaskName = "javadocJar"
val kotlinxCoroutinesVersion = "1.7.3"
val kotlinCoroutinesTest = "org.jetbrains.kotlinx:kotlinx-coroutines-test:$kotlinxCoroutinesVersion"
val junitVersion = "4.13.2"

android {
    compileSdk = androidCompileSdkVersion
    buildToolsVersion = "34.0.0"
    namespace = "com.oldguy.iocommon"

    sourceSets {
        getByName("main") {
            manifest.srcFile(androidMainDirectory.resolve("AndroidManifest.xml"))
        }
    }

    defaultConfig {
        minSdk = androidMinSdk

        buildFeatures {
            buildConfig = false
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("tools/proguard-rules.pro")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        targetCompatibility = JavaVersion.VERSION_11
    }

    dependencies {
        testImplementation("junit:junit:$junitVersion")
        androidTestImplementation("androidx.test:core:1.5.0")
        androidTestImplementation("androidx.test:runner:1.5.2")
        androidTestImplementation("androidx.test.ext:junit:1.1.5")
    }
}

tasks {
    dokkaHtml {
        moduleName.set("Kotlin Multiplatform Common IO Library")
        dokkaSourceSets {
            named("commonMain") {
                noAndroidSdkLink.set(false)
                includes.from("$appleFrameworkName.md")
            }
        }
    }
}

kotlin {
    androidTarget {
        publishLibraryVariants("release", "debug")
        mavenPublication {
            artifactId = artifactId.replace(project.name, mavenArtifactId)
        }
    }

    val githubUri = "skolson/$appleFrameworkName"
    val githubUrl = "https://github.com/$githubUri"
    cocoapods {
        ios.deploymentTarget = iosMinSdk
        summary = "Kotlin Multiplatform API for basic File I/O"
        homepage = githubUrl
        license = "Apache 2.0"
        authors = "Steven Olson"
        framework {
            baseName = appleFrameworkName
            isStatic = true
            embedBitcode(org.jetbrains.kotlin.gradle.plugin.mpp.BitcodeEmbeddingMode.BITCODE)
        }
        // Maps custom Xcode configuration to NativeBuildType
        xcodeConfigurationToNativeBuildType["CUSTOM_DEBUG"] = NativeBuildType.DEBUG
        xcodeConfigurationToNativeBuildType["CUSTOM_RELEASE"] = NativeBuildType.RELEASE
    }

    val appleXcf = XCFramework()
    macosX64 {
        binaries {
            framework {
                baseName = appleFrameworkName
                appleXcf.add(this)
                isStatic = true
            }
        }
    }
    iosX64 {
        binaries {
            framework {
                appleXcf.add(this)
                isStatic = true
                freeCompilerArgs = freeCompilerArgs + listOf("-Xoverride-konan-properties=osVersionMin=$iosMinSdk")
            }
        }
    }
    iosArm64 {
        binaries {
            framework {
                appleXcf.add(this)
                isStatic = true
                embedBitcode("bitcode")
                freeCompilerArgs = freeCompilerArgs + listOf("-Xoverride-konan-properties=osVersionMin=$iosMinSdk")
            }
        }
    }
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation(kotlinCoroutinesTest)
            }
        }
        val androidMain by getting {
            dependsOn(commonMain)
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("junit:junit:$junitVersion")
            }
        }
        val androidInstrumentedTest by getting {
            dependsOn(commonTest)
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("junit:junit:$junitVersion")
            }
        }
        val appleNativeMain by creating {
            dependsOn(commonMain)
            kotlin.srcDir("src/appleNativeMain/kotlin")
        }
        val appleNativeTest by creating {
            kotlin.srcDir("src/appleNativeTest/kotlin")
            dependsOn(commonTest)
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlinCoroutinesTest)
            }
        }
        val iosX64Main by getting {
            dependsOn(appleNativeMain)
        }
        val iosX64Test by getting {
            dependsOn(appleNativeTest)
        }
        val iosArm64Main by getting {
            dependsOn(appleNativeMain)
        }
        val macosX64Main by getting {
            dependsOn(appleNativeMain)
        }
        val macosX64Test by getting {
            dependsOn(appleNativeTest)
        }
        val jvmMain by getting {
            dependsOn(commonMain)
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("junit:junit:$junitVersion")
            }
        }
        all {
            languageSettings {
                optIn("kotlin.ExperimentalUnsignedTypes")
            }
        }
    }

    // workaround starting with Gradle 8 and kotlin 1.8.x, supposedly fixed in Kotlin 1.9.20 (KT-55751)
    val workaroundAttribute = Attribute.of("com.oldguy.kiscmp", String::class.java)
    afterEvaluate {
        configurations {
            named("debugFrameworkIosFat").configure {
                attributes.attribute(workaroundAttribute, "iosFat")
            }
            named("podDebugFrameworkIosFat").configure {
                attributes.attribute(workaroundAttribute, "podIosFat")
            }
            named("releaseFrameworkIosFat").configure {
                attributes.attribute(workaroundAttribute, "iosFat")
            }
            named("podReleaseFrameworkIosFat").configure {
                attributes.attribute(workaroundAttribute, "podIosFat")
            }
        }
    }

    publishing {
        val server = localProps.getProperty("ossrhServer")
        repositories {
            maven {
                url = uri("https://$server/service/local/staging/deploy/maven2/")
                credentials {
                    username = localProps.getProperty("ossrhUsername")
                    password = localProps.getProperty("ossrhPassword")
                }
            }
        }
        publications.withType(MavenPublication::class) {
            artifactId = artifactId.replace(project.name, mavenArtifactId)

            // workaround for https://github.com/gradle/gradle/issues/26091
            val dokkaJar = tasks.register("${this.name}DokkaJar", Jar::class) {
                group = JavaBasePlugin.DOCUMENTATION_GROUP
                description = "Dokka builds javadoc jar"
                archiveClassifier.set("javadoc")
                from(tasks.named("dokkaHtml"))
                archiveBaseName.set("${archiveBaseName.get()}-${this.name}")
            }
            artifact(dokkaJar)

            pom {
                name.set("$appleFrameworkName Kotlin Multiplatform Common File I/O")
                description.set("Library for simple Text, Binary, and Zip file I/O on supported 64 bit platforms; Android, IOS, Windows, Linux, MacOS")
                url.set(githubUrl)
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("oldguy")
                        name.set("Steve Olson")
                        email.set("skolson5903@gmail.com")
                    }
                }
                scm {
                    url.set(githubUrl)
                    connection.set("scm:git:git://git@github.com:${githubUri}.git")
                    developerConnection.set("cm:git:ssh://git@github.com:${githubUri}.git")
                }
            }
        }
    }
}

tasks.withType<Test> {
    testLogging {
        events("PASSED", "FAILED", "SKIPPED")
        exceptionFormat = TestExceptionFormat.FULL
        showStandardStreams = true
        showStackTraces = true
    }
}

task("testClasses").doLast {
    println("testClasses task Iguana workaround for KMP libraries")
}

signing {
    isRequired = true
    sign(publishing.publications)
}