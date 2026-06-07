plugins {
    id("multiloader-common")
    id("net.neoforged.moddev")
    id("com.github.gmazzo.buildconfig") version "6.0.9"
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

dependencies {
    compileOnly(group = "org.spongepowered", name = "mixin", version = "0.8.5")
    compileOnly(group = "io.github.llamalad7", name = "mixinextras-common", version = "0.5.3")
    annotationProcessor(group = "io.github.llamalad7", name = "mixinextras-common", version = "0.5.3")
    compileOnly(group = "org.ow2.asm", name = "asm", version = "9.8")
    compileOnly(group = "com.google.code.findbugs", name = "jsr305", version = "3.0.2")
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
