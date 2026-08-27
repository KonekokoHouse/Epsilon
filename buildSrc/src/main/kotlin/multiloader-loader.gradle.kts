import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("multiloader-common")
}

val commonJava by configurations.creating {
    isCanBeResolved = true
}
val commonKotlin by configurations.creating {
    isCanBeResolved = true
}
val commonResources by configurations.creating {
    isCanBeResolved = true
}

val commonProject = project(":common")
val commonBuildConfig = commonProject.tasks.named("generateBuildConfigClasses")
val commonBuildConfigJava = commonProject.layout.buildDirectory.dir("generated/sources/buildConfig/main")

dependencies {
    val loaderAttribute = Attribute.of("io.github.mcgradleconventions.loader", String::class.java)
    compileOnly(project(":common")) {
        attributes {
            attribute(loaderAttribute, "common")
        }
    }
    commonJava(project(path = ":common", configuration = "commonJava"))
    commonKotlin(project(path = ":common", configuration = "commonKotlin"))
    commonResources(project(path = ":common", configuration = "commonResources"))
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(commonJava, commonBuildConfig)
    source(commonJava)
    source(commonBuildConfigJava)
}

// :common 的 Java 类型由 compileOnly(project(":common")) 的成品 jar 提供，因此这里只需补上共享
// 的 Kotlin 源目录；javadoc 解析不了 .kt，故不接入。
tasks.named<KotlinCompile>("compileKotlin") {
    dependsOn(commonKotlin)
    source(commonKotlin)
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(commonResources)
    from(commonResources)
}

tasks.named<Javadoc>("javadoc") {
    dependsOn(commonJava, commonBuildConfig)
    source(commonJava)
    source(commonBuildConfigJava)
}

tasks.named<Jar>("sourcesJar") {
    dependsOn(commonJava)
    from(commonJava)
    dependsOn(commonKotlin)
    from(commonKotlin)
    dependsOn(commonBuildConfig)
    from(commonBuildConfigJava)
    dependsOn(commonResources)
    from(commonResources)
}
