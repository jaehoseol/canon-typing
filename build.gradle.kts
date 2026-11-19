plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.20.1"
}

group = "com.jaehoseol"
version = "0.2.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // 설치된 PyCharm 경로를 주면 그걸 쓰고, 아니면 platformVersion 버전을 내려받는다.
        val localIde = providers.gradleProperty("pycharmPath")
        if (localIde.isPresent) {
            local(localIde)
        } else {
            pycharm(providers.gradleProperty("platformVersion"))
        }
        pluginVerifier() // ./gradlew verifyPlugin 용 (Marketplace 올리기 전 호환성 검사)
    }
}

// 컴파일에 쓸 자바 버전. 기본 21. 설치된 JDK가 25뿐이면  ./gradlew buildPlugin -PjavaVersion=25
val javaVer = providers.gradleProperty("javaVersion").orElse("21").get().toInt()
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVer)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "243" // 2024.3 이상 (since-build 이상 전부 지원, 상한 없음)
            untilBuild = provider { null }
        }
        changeNotes = """
            <h4>0.2.0</h4>
            <ul>
                <li>Auto-Tempo option: steady tempo when typing fast.</li>
                <li>Play Everywhere option: terminal, plugin tool windows, search fields and dialogs.</li>
                <li>Korean IME input support.</li>
                <li>Play Test Sound action and diagnostic log entries.</li>
            </ul>
        """.trimIndent()
    }
    pluginVerification {
        ides {
            recommended() // 호환 범위의 대표 IDE 버전들로 binary 호환성 검사
        }
    }
    publishing {
        // 두 번째 업로드부터: PUBLISH_TOKEN=... ./gradlew publishPlugin  (첫 업로드는 웹에서 zip을 직접)
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
    buildSearchableOptions = false // 설정 화면이 없으므로 불필요
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}

// 안전장치: Marketplace에 올릴 zip은 JDK 21로 빌드해야 한다 (since-build 243 = 자바 21 기반 IDE).
// 자바 25 등으로 빌드한 클래스는 자바 21 IDE에서 UnsupportedClassVersionError로 로드되지 않는다.
// 내 PC에 설치해 쓰기만 할 때는  -PallowNewBytecode  를 붙이면 통과한다.
val compiledClassesDir = layout.buildDirectory.dir("classes/java/main")
val allowNewBytecode = providers.gradleProperty("allowNewBytecode").isPresent
tasks.named("buildPlugin") {
    doFirst {
        if (allowNewBytecode) return@doFirst
        val sample = compiledClassesDir.get().asFile.walkTopDown().firstOrNull { it.extension == "class" }
            ?: return@doFirst
        val b = sample.readBytes()
        val major = ((b[6].toInt() and 0xff) shl 8) or (b[7].toInt() and 0xff) // 65 = 자바 21
        if (major > 65) {
            throw GradleException(
                "컴파일된 클래스가 자바 ${major - 44} 용입니다. Marketplace/배포용은 JDK 21로 빌드해야 합니다 " +
                    "(자바 21 기반 IDE에서 로드되지 않음).\n" +
                    " - 배포용: JDK 21로  .\\gradlew buildPlugin  (-PjavaVersion 없이)\n" +
                    " - 내 PC 설치용으로만 쓸 거면:  -PallowNewBytecode  를 추가"
            )
        }
    }
}
