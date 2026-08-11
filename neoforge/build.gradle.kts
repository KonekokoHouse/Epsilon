plugins {
    id("multiloader-loader")
    alias(libs.plugins.neoforged.moddev)
}

val neoforgeVersion = project.property("neoforge_version").toString()
val modId = project.property("mod_id").toString()

dependencies {
    implementation(libs.lumin.graphics.mc.neoforge.v1211) {
        isTransitive = false
    }
    jarJar(libs.lumin.graphics.mc.neoforge.v1211) {
        isTransitive = false
        version { strictly("[${libs.versions.lumin.graphics.get()}]") }
    }
    compileOnly(libs.lumin.graphics.mc.bridge.contract) {
        isTransitive = false
    }
    implementation(libs.luaj.jse)
    jarJar(libs.luaj.jse) {
        version { strictly("[${libs.versions.luaj.get()}]") }
    }
}

neoForge {
    version = neoforgeVersion
    val at = project(":common").file("src/main/resources/META-INF/accesstransformer.cfg")
    if (at.exists()) {
        accessTransformers.from(at.absolutePath)
    }
    runs {
        configureEach {
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
            ideName = "NeoForge ${name.replaceFirstChar { it.uppercase() }} (${project.path})"
            logLevel = org.slf4j.event.Level.DEBUG
            systemProperty("terminal.jline", "true")
        }
        register("client") {
            client()
            gameDirectory = file("runs/client").also { it.mkdirs() }
        }
        register("data") {
            data()
            gameDirectory = file("runs/data").also { it.mkdirs() }
            programArguments.addAll("--mod", modId, "--all", "--output", file("src/generated/resources/").absolutePath, "--existing", file("src/main/resources/").absolutePath)
        }
    }
    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

sourceSets.main.get().resources.srcDir("src/generated/resources")

val loaderAttribute = Attribute.of("io.github.mcgradleconventions.loader", String::class.java)
listOf("apiElements", "runtimeElements", "sourcesElements").forEach { variant ->
    configurations.named(variant) {
        attributes {
            attribute(loaderAttribute, "neoforge")
        }
    }
}
sourceSets.configureEach {
    listOf(compileClasspathConfigurationName, runtimeClasspathConfigurationName, getTaskName(null, "jarJar")).forEach { variant ->
        configurations.named(variant) {
            attributes {
                attribute(loaderAttribute, "neoforge")
            }
        }
    }
}
