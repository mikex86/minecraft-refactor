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

val lwjglVersion = "2.9.3"

dependencies {
    // LWJGL dependencies
    implementation("org.lwjgl.lwjgl:lwjgl:${lwjglVersion}")
    implementation("org.lwjgl.lwjgl:lwjgl_util:${lwjglVersion}")
    
    // Include LWJGL natives for all platforms - we'll override with custom natives for macOS ARM64
    runtimeOnly("org.lwjgl.lwjgl:lwjgl-platform:${lwjglVersion}:natives-windows")
    runtimeOnly("org.lwjgl.lwjgl:lwjgl-platform:${lwjglVersion}:natives-linux")
    runtimeOnly("org.lwjgl.lwjgl:lwjgl-platform:${lwjglVersion}:natives-osx")
    
    // Additional Java libraries that might be used
    implementation("javax.vecmath:vecmath:1.5.2")

    // Test dependencies
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

// Detect whether we're running on ARM macOS
val isArm64MacOS = System.getProperty("os.name").toLowerCase().contains("mac") && 
                   System.getProperty("os.arch").toLowerCase().contains("aarch64")

// Get the absolute path to the custom natives
val customNativesPath = "${projectDir}/working_dir/natives/macos/arm64"

// Main class configuration
application {
    mainClass.set("com.mojang.minecraft.Minecraft")
    
    // JVM arguments for better performance and memory allocation
    applicationDefaultJvmArgs = if (isArm64MacOS) {
        // Use custom natives for ARM64 macOS
        listOf(
            "-Xmx1G",
            "-Djava.library.path=${customNativesPath}"
        )
    } else {
        // Use default natives for other platforms
        listOf("-Xmx1G")
    }
}

// Set the working directory for the run task
tasks.named<JavaExec>("run") {
    workingDir = file("${projectDir}/working_dir")
}

// Add a task to run with custom natives regardless of platform detection
tasks.register<JavaExec>("runWithCustomNatives") {
    group = "application"
    description = "Runs the application with custom natives for ARM64 macOS"
    
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.mojang.minecraft.Minecraft")
    
    // Set the working directory to where level.dat is located
    workingDir = file("${projectDir}/working_dir")
    
    jvmArgs = listOf(
        "-Xmx1G",
        "-Djava.library.path=${customNativesPath}"
    )
}