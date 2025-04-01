plugins {
    id("java")
    id("application")
}

group = "com.mojang"
version = "c0.0.11a"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

val lwjglVersion = "3.3.3"
val lwjglNatives = when (System.getProperty("os.name")) {
    "Mac OS X", "Darwin" -> if (System.getProperty("os.arch") == "aarch64") "natives-macos-arm64" else "natives-macos"
    "Linux" -> "natives-linux"
    else -> "natives-windows"
}

dependencies {
    // LWJGL 3 core dependencies
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-glfw")
    implementation("org.lwjgl:lwjgl-opengl")
    implementation("org.lwjgl:lwjgl-stb")     // For image loading and text rendering
    
    // Runtime natives
    runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-stb::$lwjglNatives")
    
    // Additional Java libraries
    implementation("javax.vecmath:vecmath:1.5.2")

    // Test dependencies
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

// Main class configuration
application {
    mainClass.set("com.mojang.minecraft.Minecraft")
    
    // JVM arguments for better performance and memory allocation
    applicationDefaultJvmArgs = listOf("-Xmx1G")
}

// Set the working directory for the run task
tasks.named<JavaExec>("run") {
    workingDir = file("${projectDir}/working_dir")
    
    // Add macOS-specific JVM args
    if (System.getProperty("os.name").contains("Mac")) {
        jvmArgs = jvmArgs!! + listOf("-XstartOnFirstThread")
    }
}

// For running tests
tasks.test {
    useJUnitPlatform()
}