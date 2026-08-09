import java.util.zip.ZipFile

plugins {
    id("multiloader-common")
    alias(libs.plugins.neoforged.moddev)
    alias(libs.plugins.buildconfig)
}

buildConfig {
    packageName("com.github.epsilon")
    useJavaOutput()
    buildConfigField("String", "MOD_ID", "\"${project.property("mod_id")}\"")
    val effectiveVersion = project.version.toString()
    buildConfigField("String", "VERSION", "new String(\"${effectiveVersion.replace("\\", "\\\\").replace("\"", "\\\"")}\")")
}

tasks.named("generateBuildConfigClasses") {
    inputs.file(rootProject.layout.projectDirectory.file("gradle.properties"))
        .withPropertyName("gradleProperties")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

neoForge {
    neoFormVersion = project.property("neo_form_version").toString()
    val at = file("src/main/resources/META-INF/accesstransformer.cfg")
    if (at.exists()) {
        accessTransformers.from(at.absolutePath)
    }
}

val luminGraphicsMcFabric by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    isTransitive = false
}

val luminGraphicsMcNeoForge by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    luminGraphicsMcFabric(libs.lumin.graphics.mc.fabric.v2612) {
        isTransitive = false
    }
    luminGraphicsMcNeoForge(libs.lumin.graphics.mc.neoforge.v2612) {
        isTransitive = false
    }
    compileOnly(libs.lumin.graphics.ui)
    compileOnly(libs.lumin.graphics.mc.common.v2612)
    compileOnly(libs.lumin.graphics.mc.bridge.contract) {
        isTransitive = false
    }
    compileOnly(libs.prism.rhi.backend.opengl41) {
        isTransitive = false
    }
    compileOnly(libs.prism.rhi.backend.opengl46) {
        isTransitive = false
    }
    compileOnly(libs.mixin)
    compileOnly(libs.mixinextras.common)
    annotationProcessor(libs.mixinextras.common)
    compileOnly(libs.asm)
    compileOnly(libs.jsr305)
}

configurations {
    create("commonJava") {
        isCanBeResolved = false
        isCanBeConsumed = true
    }
    create("commonResources") {
        isCanBeResolved = false
        isCanBeConsumed = true
    }
}

artifacts {
    add("commonJava", file("src/main/java"))
    add("commonResources", file("src/main/resources"))
}

val loaderAttribute = Attribute.of("io.github.mcgradleconventions.loader", String::class.java)

listOf("apiElements", "runtimeElements", "sourcesElements").forEach { variant ->
    configurations.named(variant) {
        attributes {
            attribute(loaderAttribute, "common")
        }
    }
}

sourceSets.configureEach {
    listOf(compileClasspathConfigurationName, runtimeClasspathConfigurationName).forEach { variant ->
        configurations.named(variant) {
            attributes {
                attribute(loaderAttribute, "common")
            }
        }
    }
}

val verifyLuminJarInJarArchives = tasks.register("verifyLuminJarInJarArchives") {
    group = "verification"
    description = "Verifies that final archives contain only their matching Lumin Graphics-MC loader."
    dependsOn(":fabric:remapJar", ":neoforge:jar")

    doLast {
        val fabricOuter = rootProject.project(":fabric").tasks.named<Jar>("jar").get().archiveFile.get().asFile
        val neoForgeOuter = rootProject.project(":neoforge").tasks.named<Jar>("jar").get().archiveFile.get().asFile
        fun verifyOuter(outer: File, nestedDirectory: String, expectedLoader: File, metadata: String) {
            ZipFile(outer).use { archive ->
                val nested = archive.entries().asSequence()
                    .map { it.name }
                    .filter { it.startsWith(nestedDirectory) && it.endsWith(".jar") }
                    .toList()
                check("$nestedDirectory${expectedLoader.name}" in nested) {
                    "${outer.name} is missing ${expectedLoader.name}: $nested"
                }
                check(nested.none { it.contains("mc-26.1.2-common") || it.contains("bridge-contract") }) {
                    "${outer.name} must not embed Lumin common or bridge artifacts: $nested"
                }
                check(archive.getEntry(metadata) != null) {
                    "${outer.name} is missing $metadata"
                }
            }
        }
        verifyOuter(fabricOuter, "META-INF/jars/", luminGraphicsMcFabric.singleFile, "fabric.mod.json")
        verifyOuter(neoForgeOuter, "META-INF/jarjar/", luminGraphicsMcNeoForge.singleFile,
            "META-INF/jarjar/metadata.json")
    }
}

rootProject.tasks.register("verifyLuminJarInJar") {
    group = "verification"
    description = "Runs the loader-specific Lumin Graphics-MC Jar-in-Jar verification gate."
    dependsOn(verifyLuminJarInJarArchives)
}
