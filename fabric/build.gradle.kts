import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    id("multiloader-loader")
    alias(libs.plugins.fabric.loom)
}

val modId = project.property("mod_id").toString()

// Lumin Graphics-MC 发布的 fabric.mod.json 把 fabricloader 与 fabric-api 写成 "=" 精确依赖。
// Fabric Loader 对依赖不满足的内嵌 mod 只是静默排除，不会报错，因此玩家一旦更新 Loader 或
// Fabric API，整个 Lumin 运行时就会从 classpath 消失，Epsilon 直到 ModuleHolder.initModules
// 首次触碰 Lumin class 才抛 NoClassDefFoundError。这里在 Loom 把内嵌 jar 暂存到
// processIncludeJars 输出目录之后、写进成品 jar 之前改写这两个精确依赖为下限；minecraft 与
// java 保持原样，Lumin 的 MC 绑定确实只对应单一游戏版本。
val luminGraphicsMcFabricModule = libs.lumin.graphics.mc.fabric.v2612.get()

// 内嵌 jar 沿用上游文件名，Loom 按模块坐标暂存，:common 的 verifyLuminJarInJar 门禁也按此名断言。
val luminGraphicsMcFabricJarName =
    "${luminGraphicsMcFabricModule.module.name}-${luminGraphicsMcFabricModule.version}.jar"

tasks.named("processIncludeJars") {
    val nestedJarName = luminGraphicsMcFabricJarName
    val stagedJars: FileCollection = outputs.files

    doLast {
        val source = stagedJars.singleFile.resolve(nestedJarName)
        check(source.isFile) { "Loom did not stage $nestedJarName for nesting" }
        val target = File(source.parentFile, "$nestedJarName.relaxed")

        var relaxedMetadata = false
        ZipFile(source).use { archive ->
            ZipOutputStream(target.outputStream().buffered()).use { output ->
                for (entry in archive.entries()) {
                    val copied = ZipEntry(entry.name)
                    copied.time = entry.time
                    if (entry.name == "fabric.mod.json") {
                        val original = archive.getInputStream(entry).use { stream -> stream.readBytes() }
                            .toString(Charsets.UTF_8)
                        val relaxed = original.replace(
                            Regex("\"(fabricloader|fabric-api)\"(\\s*:\\s*\")=")
                        ) { match -> "\"${match.groupValues[1]}\"${match.groupValues[2]}>=" }
                        output.putNextEntry(copied)
                        output.write(relaxed.toByteArray(Charsets.UTF_8))
                        relaxedMetadata = true
                    } else {
                        // 逐条保留原始压缩方式，STORED 条目必须自带 size 与 crc。
                        if (entry.method == ZipEntry.STORED) {
                            copied.method = ZipEntry.STORED
                            copied.size = entry.size
                            copied.crc = entry.crc
                        }
                        output.putNextEntry(copied)
                        if (!entry.isDirectory) {
                            archive.getInputStream(entry).use { stream -> stream.copyTo(output) }
                        }
                    }
                    output.closeEntry()
                }
            }
        }
        check(relaxedMetadata) { "$nestedJarName does not contain fabric.mod.json" }
        check(source.delete() && target.renameTo(source)) {
            "Failed to replace $nestedJarName with relaxed metadata"
        }
    }
}

dependencies {
    minecraft(libs.minecraft)
    implementation(libs.lumin.graphics.mc.fabric.v2612) {
        isTransitive = false
    }
    include(libs.lumin.graphics.mc.fabric.v2612) {
        isTransitive = false
    }
    compileOnly(libs.lumin.graphics.mc.bridge.contract) {
        isTransitive = false
    }
    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)
    implementation(libs.luaj.jse)
    include(libs.luaj.jse)
    include(libs.kotlin.stdlib) {
        isTransitive = false
    }
    compileOnly(libs.sodium.fabric)
    compileOnly(libs.jsr305)
}

loom {
    val aw = project(":common").file("src/main/resources/${modId}.accesswidener")
    if (aw.exists()) {
        accessWidenerPath.set(aw)
    }
    runs {
        named("client") {
            client()
            configName = "Fabric Client"
            ideConfigGenerated(true)
            runDir("runs/client")
        }
    }
}

tasks.register("remapJar") {
    group = "build"
    description = "Builds the final Fabric archive; Mojang mappings require no separate remap pass."
    dependsOn(tasks.named("jar"))
}

val loaderAttribute = Attribute.of("io.github.mcgradleconventions.loader", String::class.java)
listOf("apiElements", "runtimeElements", "sourcesElements", "includeInternal", "modCompileClasspath").forEach { variant ->
    configurations.named(variant) {
        attributes {
            attribute(loaderAttribute, "fabric")
        }
    }
}
sourceSets.configureEach {
    listOf(compileClasspathConfigurationName, runtimeClasspathConfigurationName).forEach { variant ->
        configurations.named(variant) {
            attributes {
                attribute(loaderAttribute, "fabric")
            }
        }
    }
}

/*
tasks.register<Copy>("extractRuntimeClasspath") {
    from(configurations.runtimeClasspath)
    into("$projectDir/build/runtimeClasspath")
    doFirst {
        file("$projectDir/build/runtimeClasspath").mkdirs()
    }
}
*/
