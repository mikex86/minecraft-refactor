plugins {
    id("java-library")
}

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

val lwjglVersion = "3.4.1"
val lwjglNatives = when (System.getProperty("os.name")) {
    "Mac OS X", "Darwin" -> if (System.getProperty("os.arch") == "aarch64") "natives-macos-arm64" else "natives-macos"
    "Linux" -> "natives-linux"
    else -> "natives-windows"
}

dependencies {
    api(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    api("org.lwjgl:lwjgl")
    api("org.lwjgl:lwjgl-glfw")
    api("org.lwjgl:lwjgl-opengl")
    api("org.lwjgl:lwjgl-vulkan")
    api("org.lwjgl:lwjgl-shaderc")
    api("org.lwjgl:lwjgl-jemalloc")

    runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-shaderc::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-jemalloc::$lwjglNatives")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

val shaderTools by sourceSets.creating

dependencies {
    add(shaderTools.implementationConfigurationName, platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    add(shaderTools.implementationConfigurationName, "org.lwjgl:lwjgl")
    add(shaderTools.implementationConfigurationName, "org.lwjgl:lwjgl-shaderc")

    add(shaderTools.runtimeOnlyConfigurationName, "org.lwjgl:lwjgl::$lwjglNatives")
    add(shaderTools.runtimeOnlyConfigurationName, "org.lwjgl:lwjgl-shaderc::$lwjglNatives")
}

val generatedTestShaderOutputDir = layout.buildDirectory.dir("generated/resources/test/shaders")

val compileTestShaders by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Compiles test GLSL shaders to SPIR-V binaries with Shaderc."
    classpath = shaderTools.runtimeClasspath
    mainClass.set("com.mojang.minecraft.tools.ShaderBinaryCompiler")
    args(
            file("src/test/resources/shaders").absolutePath,
            generatedTestShaderOutputDir.get().asFile.absolutePath
    )
    inputs.dir(file("src/test/resources/shaders"))
    outputs.dir(generatedTestShaderOutputDir)
}

tasks.processTestResources {
    dependsOn(compileTestShaders)
    from(generatedTestShaderOutputDir) {
        into("shaders")
    }
}

tasks.test {
    useJUnitPlatform()
}
