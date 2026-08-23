// 根构建脚本：只声明插件版本（apply false），桌面工程仍由 pom.xml（Maven）管理。
plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0" apply false
}
