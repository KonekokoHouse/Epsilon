plugins {
    id("multiloader-loader")
    id("fabric-loom")
}

val modId = project.property("mod_id").toString()

dependencies {
    minecraft(libs.minecraft)
    modRuntimeOnly(libs.lumin.graphics.mc.fabric.v1211) {
        isTransitive = false
    }
    include(libs.lumin.graphics.mc.fabric.v1211) {
        isTransitive = false
    }
    compileOnly(libs.lumin.graphics.ui) {
        exclude(group = "org.lwjgl")
    }
    compileOnly(libs.lumin.graphics.mc.common.v1211) {
        exclude(group = "org.lwjgl")
    }
    compileOnly(libs.lumin.graphics.mc.bridge.contract) {
        isTransitive = false
    }
    compileOnly(libs.prism.rhi.backend.opengl41) {
        isTransitive = false
    }
    compileOnly(libs.prism.rhi.backend.opengl46) {
        isTransitive = false
    }
    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabric.api)
    compileOnly(libs.mixin)
    compileOnly(libs.mixinextras.common)
    annotationProcessor(libs.mixinextras.common)
    compileOnly(libs.asm)
    compileOnly(libs.jsr305)
}

dependencies {
    mappings(loom.officialMojangMappings())
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
