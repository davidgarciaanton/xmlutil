/*
 * Copyright (c) 2018.
 *
 * This file is part of XmlUtil.
 *
 * This file is licenced to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You should have received a copy of the license with the source distribution.
 * Alternatively, you may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import com.jfrog.bintray.gradle.BintrayExtension
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*

plugins {
    kotlin("multiplatform")
    id("kotlinx-serialization")
    id("maven-publish")
    id("com.jfrog.bintray")
    idea
}

val xmlutil_version: String by project
val xmlutil_versiondesc: String by project

base {
    archivesBaseName = "serialutil"
    version = xmlutil_version
}

val serializationVersion: String by project

val kotlin_version: String by project

val androidAttribute = Attribute.of("net.devrieze.android", Boolean::class.javaObjectType)
val javaVersionAttribute = Attribute.of("net.devrieze.javaVersion", String::class.java)

val moduleName = "net.devrieze.serialutil"

val moduleName = "net.devrieze.serialutil"

val moduleName = "net.devrieze.serialutil"

kotlin {
    targets {
        jvm("jvm9") {
            attributes.attribute(javaVersionAttribute, JavaVersion.VERSION_1_9.toString())
            withJava()

            compilations.all {
                tasks.withType<KotlinCompile> {
                    kotlinOptions {
                        jvmTarget = "9"
                        freeCompilerArgs = listOf("-Xuse-experimental=kotlin.Experimental")
                    }
                }
                if (name=="main") {
                    tasks.withType<JavaCompile> {
                        destinationDir = tasks.named<KotlinCompile>(compileKotlinTaskName).get().destinationDir
                    }
                }
            }
        }
        jvm {
            attributes.attribute(javaVersionAttribute, JavaVersion.VERSION_1_8.toString())
            compilations.all {
                tasks.named<KotlinCompile>(compileKotlinTaskName) {
                    kotlinOptions {
                        jvmTarget = "1.8"
                        freeCompilerArgs = listOf("-Xuse-experimental=kotlin.Experimental")
                    }
                }
                tasks.named<Jar>("jvmJar") {
                    manifest {
                        attributes("Automatic-Module-Name" to moduleName)
                    }
                }
            }
            attributes.attribute(androidAttribute, false)
        }
        jvm("android") {
            attributes {
                attribute(androidAttribute, true)
                attribute(javaVersionAttribute, JavaVersion.VERSION_1_6.toString())
                attribute(KotlinPlatformType.attribute, KotlinPlatformType.androidJvm)
            }
            compilations.all {
                tasks.getByName<KotlinCompile>(compileKotlinTaskName).kotlinOptions {
                    jvmTarget = "1.6"
                    freeCompilerArgs = listOf("-Xuse-experimental=kotlin.Experimental")
                }
            }
        }
        js {
            browser()
            nodejs()
            compilations.all {
                tasks.getByName<KotlinJsCompile>(compileKotlinTaskName).kotlinOptions {
                    sourceMap = true
                    sourceMapEmbedSources = "always"
                    suppressWarnings = false
                    verbose = true
                    metaInfo = true
                    moduleKind = "umd"
                    main = "call"
                    freeCompilerArgs = listOf("-Xuse-experimental=kotlin.Experimental")
                }
            }
        }

        forEach { target ->
            target.mavenPublication {
                groupId = "net.devrieze"
                artifactId = "serialutil-${target.targetName}"
                version = xmlutil_version
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
                api("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationVersion")
            }
        }
        val javaShared by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(kotlin("stdlib-jdk7"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:$serializationVersion")
            }
        }
        val jvmMain by getting {
            dependsOn(javaShared)
            dependencies {
                implementation(kotlin("stdlib-jdk7"))
            }
        }
        val jvm9Main by getting {
            dependsOn(jvmMain)
            dependencies {
                api("org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version:modular")
            }
        }
        val androidMain by getting {
            dependsOn(javaShared)
        }
        val jsMain by getting {
            dependsOn(commonMain)
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-stdlib-js:$kotlin_version")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime-js:$serializationVersion")
            }
        }

    }

}

tasks.named<JavaCompile>("compileJava") {
    sourceCompatibility = "9"
    targetCompatibility = "9"
    doFirst {
        options.compilerArgs = listOf(
            "--module-path", classpath.asPath,
            "--patch-module", "$moduleName=${sourceSets["main"].output.asPath}"
                                     )
        classpath = files()
    }
}

tasks.named<Jar>("jar") {
    
}

dependencies {
    "compileClasspath"("org.jetbrains.kotlin:kotlin-stdlib:modular")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_9
    targetCompatibility = JavaVersion.VERSION_1_9
}

dependencies {
    "compileClasspath"("org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version:modular")
}

configurations.named("compileClasspath") {
    attributes.attribute(javaVersionAttribute, JavaVersion.VERSION_1_9.toString())
}

repositories {
    jcenter()
    maven { setUrl("https://dl.bintray.com/kotlin/kotlin-eap") }
}

publishing.publications.getByName<MavenPublication>("kotlinMultiplatform") {
    groupId = "net.devrieze"
    artifactId = "serialutil"
}

extensions.configure<BintrayExtension>("bintray") {
    if (rootProject.hasProperty("bintrayUser")) {
        user = rootProject.property("bintrayUser") as String?
        key = rootProject.property("bintrayApiKey") as String?
    }

    val pubs = publishing.publications
        .filter { it.name != "metadata" }
        .map { it.name }
        .apply { forEach { logger.lifecycle("Registering publication \"$it\" to Bintray") } }
        .toTypedArray()


    setPublications(*pubs)

    pkg(closureOf<BintrayExtension.PackageConfig> {
        repo = "maven"
        name = "serialutil"
        userOrg = "pdvrieze"
        setLicenses("Apache-2.0")
        vcsUrl = "https://github.com/pdvrieze/xmlutil.git"

        version.apply {
            name = xmlutil_version
            desc = xmlutil_versiondesc
            released = Date().toString()
            vcsTag = "v$version"
        }
    })

}

idea {
    module {
        name = "xmlutil-serialutil"
        languageLevel = IdeaLanguageLevel(JavaVersion.VERSION_1_9)
    }
}