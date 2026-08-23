plugins {
    `java-library`
}

group = "com.sojourners.chess"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// 仓库统一由根 settings.gradle.kts 提供（google + mavenCentral）

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
        showStackTraces = true
    }
}

/**
 * 纯净性约束：core 必须保持零 UI/桌面/JNI/JDBC/虚拟线程依赖（ANDROID_PLAN.md §3.3）。
 */
val forbidden = listOf(
    "javafx",
    "java\\.awt",
    "javax\\.swing",
    "com\\.sun\\.jna",
    "jnativehook",
    "import java\\.sql",
    "ofVirtual",
    "startVirtualThread"
)

tasks.register("verifyPureJava") {
    group = "verification"
    description = "Fails if core sources import desktop-only APIs."
    doLast {
        val violations = mutableListOf<String>()
        fileTree("src/main/java") { include("**/*.java") }.forEach { f ->
            f.readLines().forEachIndexed { idx, line ->
                val t = line.trim()
                if (t.startsWith("import ") || t.contains("Thread.")) {
                    forbidden.forEach { p ->
                        if (Regex(p).containsMatchIn(t)) {
                            violations += "${f.name}:${idx + 1}: [$p] $t"
                        }
                    }
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException("core 纯净性检查失败:\n" + violations.joinToString("\n"))
        }
    }
}

tasks.named("check") {
    dependsOn("verifyPureJava")
}
