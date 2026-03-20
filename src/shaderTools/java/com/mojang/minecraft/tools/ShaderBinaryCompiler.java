package com.mojang.minecraft.tools;

import org.lwjgl.util.shaderc.Shaderc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class ShaderBinaryCompiler {

    private ShaderBinaryCompiler() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected exactly two arguments: <inputShaderDir> <outputDir>");
        }

        Path inputRoot = Paths.get(args[0]);
        Path outputRoot = Paths.get(args[1]);

        if (!Files.exists(inputRoot)) {
            System.out.println("Shader source directory does not exist, skipping: " + inputRoot);
            return;
        }

        recreateDirectory(outputRoot);

        long compiler = Shaderc.shaderc_compiler_initialize();
        if (compiler == 0L) {
            throw new IllegalStateException("Failed to initialize Shaderc compiler");
        }

        long openGlOptions = Shaderc.shaderc_compile_options_initialize();
        if (openGlOptions == 0L) {
            Shaderc.shaderc_compiler_release(compiler);
            throw new IllegalStateException("Failed to initialize Shaderc compile options");
        }
        long vulkanOptions = Shaderc.shaderc_compile_options_initialize();
        if (vulkanOptions == 0L) {
            Shaderc.shaderc_compile_options_release(openGlOptions);
            Shaderc.shaderc_compiler_release(compiler);
            throw new IllegalStateException("Failed to initialize Vulkan Shaderc compile options");
        }

        try {
            configureOpenGlOptions(openGlOptions);
            configureVulkanOptions(vulkanOptions);

            List<Path> shaderFiles = new ArrayList<>();
            try (Stream<Path> paths = Files.walk(inputRoot)) {
                paths.filter(Files::isRegularFile)
                        .forEach(shaderFiles::add);
            }
            shaderFiles.sort(Comparator.naturalOrder());

            int compiled = 0;
            for (Path shaderFile : shaderFiles) {
                int kind = inferShaderKind(shaderFile);
                if (kind == -1) {
                    continue;
                }
                compileOne(compiler, openGlOptions, inputRoot, outputRoot, shaderFile, kind, ".spv");
                compileOne(compiler, vulkanOptions, inputRoot, outputRoot, shaderFile, kind, ".vk.spv");
                compiled++;
            }

            System.out.println("Compiled " + compiled + " shader(s) to OpenGL+Vulkan SPIR-V under " + outputRoot);
        } finally {
            Shaderc.shaderc_compile_options_release(vulkanOptions);
            Shaderc.shaderc_compile_options_release(openGlOptions);
            Shaderc.shaderc_compiler_release(compiler);
        }
    }

    private static void configureOpenGlOptions(long options) {
        Shaderc.shaderc_compile_options_set_source_language(options, Shaderc.shaderc_source_language_glsl);
        Shaderc.shaderc_compile_options_set_target_env(options, Shaderc.shaderc_target_env_opengl, Shaderc.shaderc_env_version_opengl_4_5);
        Shaderc.shaderc_compile_options_set_target_spirv(options, Shaderc.shaderc_spirv_version_1_0);
        Shaderc.shaderc_compile_options_set_optimization_level(options, Shaderc.shaderc_optimization_level_performance);
    }

    private static void configureVulkanOptions(long options) {
        Shaderc.shaderc_compile_options_set_source_language(options, Shaderc.shaderc_source_language_glsl);
        Shaderc.shaderc_compile_options_set_target_env(options, Shaderc.shaderc_target_env_vulkan, Shaderc.shaderc_env_version_vulkan_1_0);
        Shaderc.shaderc_compile_options_set_target_spirv(options, Shaderc.shaderc_spirv_version_1_0);
        Shaderc.shaderc_compile_options_add_macro_definition(options, "VULKAN_BACKEND", "1");
        Shaderc.shaderc_compile_options_set_optimization_level(options, Shaderc.shaderc_optimization_level_performance);
    }

    private static void compileOne(long compiler, long options, Path inputRoot, Path outputRoot, Path shaderFile, int kind, String suffix) throws IOException {
        String source = new String(Files.readAllBytes(shaderFile), StandardCharsets.UTF_8);
        String relativePath = toUnixPath(inputRoot.relativize(shaderFile));

        long result = Shaderc.shaderc_compile_into_spv(compiler, source, kind, relativePath, "main", options);
        if (result == 0L) {
            throw new IllegalStateException("Shaderc returned null result for " + relativePath);
        }

        try {
            int status = Shaderc.shaderc_result_get_compilation_status(result);
            if (status != Shaderc.shaderc_compilation_status_success) {
                String error = Shaderc.shaderc_result_get_error_message(result);
                throw new IllegalStateException("Failed to compile shader '" + relativePath + "':\n" + error);
            }

            ByteBuffer bytes = Shaderc.shaderc_result_get_bytes(result);
            if (bytes == null) {
                throw new IllegalStateException("Shaderc produced no bytes for " + relativePath);
            }

            byte[] output = new byte[bytes.remaining()];
            bytes.get(output);

            Path outputFile = outputRoot.resolve(relativePath + suffix);
            Path parent = outputFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(outputFile, output);
        } finally {
            Shaderc.shaderc_result_release(result);
        }
    }

    private static int inferShaderKind(Path path) {
        String file = path.getFileName().toString();
        if (file.endsWith(".vert")) {
            return Shaderc.shaderc_glsl_vertex_shader;
        }
        if (file.endsWith(".frag")) {
            return Shaderc.shaderc_glsl_fragment_shader;
        }
        if (file.endsWith(".comp")) {
            return Shaderc.shaderc_glsl_compute_shader;
        }
        return -1;
    }

    private static void recreateDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            List<Path> toDelete = new ArrayList<>();
            try (Stream<Path> paths = Files.walk(dir)) {
                paths.forEach(toDelete::add);
            }
            toDelete.sort(Comparator.reverseOrder());
            for (Path path : toDelete) {
                Files.delete(path);
            }
        }
        Files.createDirectories(dir);
    }

    private static String toUnixPath(Path path) {
        return path.toString().replace('\\', '/');
    }
}
