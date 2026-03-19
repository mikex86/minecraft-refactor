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

tasks.test {
    useJUnitPlatform()
}
