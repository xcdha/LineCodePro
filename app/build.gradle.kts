import java.security.SecureRandom

plugins {
    alias(libs.plugins.android.application)
}

val generateReleaseObfuscationDictionary by tasks.registering {
    val outputFile = layout.buildDirectory.file("generated/r8/obfuscation-dictionary.txt")
    outputs.file(outputFile)
    outputs.upToDateWhen { false }

    doLast {
        val random = SecureRandom()
        val alphabet = "abcdefghijklmnopqrstuvwxyz"
        val names = linkedSetOf<String>()

        fun randomName(): String = buildString {
            append('l')
            repeat(15) {
                append(alphabet[random.nextInt(alphabet.length)])
            }
        }

        while (names.size < 8192) {
            names += randomName()
        }

        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(names.joinToString(System.lineSeparator()) + System.lineSeparator())
    }
}

val purgeReleaseSymbolFiles by tasks.registering(Delete::class) {
    delete(layout.buildDirectory.dir("outputs/mapping/release"))
    delete(layout.buildDirectory.dir("outputs/native-debug-symbols/release"))
}

android {
    namespace = "cn.lineai"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "cn.lineai"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "NONE"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.matching {
    it.name == "minifyReleaseWithR8" || it.name == "minifyReleaseWithProguard"
}.configureEach {
    dependsOn(generateReleaseObfuscationDictionary)
}

tasks.matching {
    it.name == "assembleRelease" || it.name == "bundleRelease"
}.configureEach {
    finalizedBy(purgeReleaseSymbolFiles)
}

dependencies {
    implementation(libs.commonmark)
    implementation(libs.commonmark.gfm.tables)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
