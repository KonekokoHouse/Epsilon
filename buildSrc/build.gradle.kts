plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    // kotlin-dsl 只把 KGP 放进 buildSrc 自身的 buildscript 类路径，而预编译脚本插件里的
    // plugins { id(...) } 只从 buildSrc 的实现类路径解析插件，所以必须显式声明一次。
    implementation(libs.kotlin.gradle.plugin)
}
