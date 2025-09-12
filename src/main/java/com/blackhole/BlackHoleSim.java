package com.blackhole;

import org.joml.*;
import org.joml.Math;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class BlackHoleSim {
    // Constants
    private static final double G = 6.67430e-11;

    // Window & Compute settings
    private int WIDTH = 800;
    private int HEIGHT = 600;
    private int COMPUTE_WIDTH = 200;
    private int COMPUTE_HEIGHT = 150;
    private long window;

    // OpenGL handles
    private int quadShaderProgram, computeProgram;
    private int quadVAO, texture;
    private int cameraUBO, diskUBO, objectsUBO;


    // Simulation state
    public static boolean GRAVITY_ENABLED = false;
    private Camera camera = new Camera();
    private BlackHole sagA = new BlackHole(new Vector3f(0), 8.54e36);
    private List<ObjectData> objects = new ArrayList<>();

    public void run() {
        init();
        loop();
        cleanup();
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

        window = glfwCreateWindow(WIDTH, HEIGHT, "project13", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Failed to create the GLFW window");

        camera.setupCallbacks(window);
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        System.out.println("OpenGL " + glGetString(GL_VERSION));

        createObjects();
        setupShaders();
        setupQuad();
        setupUBOs();
    }

    private void createObjects() {
        objects.add(new ObjectData(new Vector4f(4e11f, 0.0f, 0.0f, 4e10f), new Vector4f(1, 1, 0, 1), 1.98892e30f));
        objects.add(new ObjectData(new Vector4f(0.0f, 0.0f, 4e11f, 4e10f), new Vector4f(1, 0, 0, 1), 1.98892e30f));
        objects.add(new ObjectData(new Vector4f(0.0f, 0.0f, 0.0f, (float) sagA.r_s), new Vector4f(0, 0, 0, 1), (float) sagA.mass));
    }

    private void setupShaders() {
        // Quad shader for displaying the texture
        String quadVertSrc = "#version 330 core\nlayout (location = 0) in vec2 aPos; layout (location = 1) in vec2 aTexCoord; out vec2 TexCoord; void main() { gl_Position = vec4(aPos, 0.0, 1.0); TexCoord = aTexCoord; }";
        String quadFragSrc = "#version 330 core\nin vec2 TexCoord; out vec4 FragColor; uniform sampler2D screenTexture; void main() { FragColor = texture(screenTexture, TexCoord); }";
        quadShaderProgram = createShaderProgram(quadVertSrc, quadFragSrc);

        // Compute shader
        computeProgram = createComputeProgram(loadResource("geodesic.comp"));
    }

    private void setupQuad() {
        float[] quadVertices = {
                // positions   // texCoords
                -1.0f,  1.0f,  0.0f, 1.0f,
                -1.0f, -1.0f,  0.0f, 0.0f,
                1.0f, -1.0f,  1.0f, 0.0f,

                -1.0f,  1.0f,  0.0f, 1.0f,
                1.0f, -1.0f,  1.0f, 0.0f,
                1.0f,  1.0f,  1.0f, 1.0f
        };

        quadVAO = glGenVertexArrays();
        int vbo = glGenBuffers();
        glBindVertexArray(quadVAO);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, quadVertices, GL_STATIC_DRAW);

        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);

        // Texture setup
        texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, COMPUTE_WIDTH, COMPUTE_HEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
    }


    private void setupUBOs() {
        cameraUBO = glGenBuffers();
        glBindBuffer(GL_UNIFORM_BUFFER, cameraUBO);
        glBufferData(GL_UNIFORM_BUFFER, 128, GL_DYNAMIC_DRAW);
        glBindBufferBase(GL_UNIFORM_BUFFER, 1, cameraUBO);

        diskUBO = glGenBuffers();
        glBindBuffer(GL_UNIFORM_BUFFER, diskUBO);
        glBufferData(GL_UNIFORM_BUFFER, 4 * Float.BYTES, GL_DYNAMIC_DRAW);
        glBindBufferBase(GL_UNIFORM_BUFFER, 2, diskUBO);

        objectsUBO = glGenBuffers();
        glBindBuffer(GL_UNIFORM_BUFFER, objectsUBO);
        int objUBOSize = 4 * Integer.BYTES + 16 * (4 * Float.BYTES) + 16 * (4 * Float.BYTES) + 16 * Float.BYTES;
        glBufferData(GL_UNIFORM_BUFFER, objUBOSize, GL_DYNAMIC_DRAW);
        glBindBufferBase(GL_UNIFORM_BUFFER, 3, objectsUBO);
    }


    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            camera.update();
            updatePhysics();


            // --- RAYTRACER ---
            glViewport(0, 0, WIDTH, HEIGHT);
            dispatchCompute();
            drawFullScreenQuad();

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void updatePhysics() {
        if (!GRAVITY_ENABLED) return;

        for (ObjectData obj1 : objects) {
            for (ObjectData obj2 : objects) {
                if (obj1 == obj2) continue;

                float dx = obj2.posRadius.x - obj1.posRadius.x;
                float dy = obj2.posRadius.y - obj1.posRadius.y;
                float dz = obj2.posRadius.z - obj1.posRadius.z;
                float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

                if (dist > 0) {
                    Vector3d direction = new Vector3d(dx / dist, dy / dist, dz / dist);
                    double force = (G * obj1.mass * obj2.mass) / (dist * dist);
                    double acc1 = force / obj1.mass;

                    Vector3d acceleration = direction.mul(acc1);
                    obj1.velocity.add((float)acceleration.x, (float)acceleration.y, (float)acceleration.z);
                    obj1.posRadius.x += obj1.velocity.x;
                    obj1.posRadius.y += obj1.velocity.y;
                    obj1.posRadius.z += obj1.velocity.z;
                }
            }
        }
    }

    private void dispatchCompute() {
        int cw = camera.moving ? 100: COMPUTE_WIDTH;
        int ch = camera.moving ? 75: COMPUTE_HEIGHT;

        glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, cw, ch, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);

        glUseProgram(computeProgram);
        uploadCameraUBO();
        uploadDiskUBO();
        uploadObjectsUBO();

        glBindImageTexture(0, texture, 0, false, 0, GL_WRITE_ONLY, GL_RGBA8);

        int groupsX = (int) Math.ceil(cw / 16.0f);
        int groupsY = (int) Math.ceil(ch / 16.0f);
        glDispatchCompute(groupsX, groupsY, 1);
        glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
    }

    private void drawFullScreenQuad() {
        glUseProgram(quadShaderProgram);
        glBindVertexArray(quadVAO);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture);
        glUniform1i(glGetUniformLocation(quadShaderProgram, "screenTexture"), 0);

        glDisable(GL_DEPTH_TEST);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glEnable(GL_DEPTH_TEST);
    }

    // --- UBO Upload Methods ---
    private void uploadCameraUBO() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Vector3f camPos = camera.getPosition();
            Vector3f fwd = new Vector3f(camera.target).sub(camPos).normalize();
            Vector3f right = new Vector3f(fwd).cross(0, 1, 0).normalize();
            Vector3f up = new Vector3f(right).cross(fwd);

            // Allocate a ByteBuffer of exactly 128 bytes, the size of our UBO.
            ByteBuffer uboBuffer = stack.malloc(128);

            // Put vectors at their respective 16-byte aligned offsets
            camPos.get(0, uboBuffer);
            right.get(16, uboBuffer);
            up.get(32, uboBuffer);
            fwd.get(48, uboBuffer);

            // Put floats
            uboBuffer.putFloat(64, (float)Math.tan(Math.toRadians(60.0 * 0.5)));
            uboBuffer.putFloat(68, (float)WIDTH / HEIGHT);
            // In GLSL, a 'bool' in std140 layout is treated as a 4-byte integer (int/uint).
            // Let's use putInt to be explicit.
            uboBuffer.putInt(72, camera.moving ? 1 : 0);

            glBindBuffer(GL_UNIFORM_BUFFER, cameraUBO);
            glBufferSubData(GL_UNIFORM_BUFFER, 0, uboBuffer);
        }
    }

    private void uploadDiskUBO() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            float r1 = (float)sagA.r_s * 2.2f;
            float r2 = (float)sagA.r_s * 5.2f;
            float thickness = 1e9f;
            FloatBuffer fb = stack.floats(r1, r2, 2.0f, thickness);

            glBindBuffer(GL_UNIFORM_BUFFER, diskUBO);
            glBufferSubData(GL_UNIFORM_BUFFER, 0, fb);
        }
    }

    private void uploadObjectsUBO() {
        final int VEC4_SIZE = 16;
        final int MAX_OBJECTS = 16;

        // Calculate offsets based on std140 layout rules
        final int NUM_OBJECTS_OFFSET = 0;
        final int POS_RADIUS_OFFSET = 16;
        final int COLOR_OFFSET = POS_RADIUS_OFFSET + MAX_OBJECTS * VEC4_SIZE;
        final int MASS_OFFSET = COLOR_OFFSET + MAX_OBJECTS * VEC4_SIZE;
        final int UBO_SIZE = MASS_OFFSET + MAX_OBJECTS * VEC4_SIZE;

        int numObjects = Math.min(objects.size(), MAX_OBJECTS);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer uboBuffer = stack.malloc(UBO_SIZE);

            // 1. Put the number of objects (as an int)
            uboBuffer.putInt(NUM_OBJECTS_OFFSET, numObjects);

            // 2. Fill the posRadius array
            for (int i = 0; i < numObjects; i++) {
                objects.get(i).posRadius.get(POS_RADIUS_OFFSET + i * VEC4_SIZE, uboBuffer);
            }

            // 3. Fill the color array
            for (int i = 0; i < numObjects; i++) {
                objects.get(i).color.get(COLOR_OFFSET + i * VEC4_SIZE, uboBuffer);
            }

            // 4. Fill the mass array. IMPORTANT: Each float in a float array is padded to 16 bytes.
            for (int i = 0; i < numObjects; i++) {
                uboBuffer.putFloat(MASS_OFFSET + i * VEC4_SIZE, objects.get(i).mass);
            }

            // Upload the entire buffer at once
            glBindBuffer(GL_UNIFORM_BUFFER, objectsUBO);
            glBufferSubData(GL_UNIFORM_BUFFER, 0, uboBuffer);
        }
    }
    // --- Utility Methods ---
    private String loadResource(String fileName) {
        try (InputStream is = BlackHoleSim.class.getResourceAsStream("/" + fileName);
             Scanner scanner = new Scanner(is, StandardCharsets.UTF_8)) {
            return scanner.useDelimiter("\\A").next();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load resource: " + fileName, e);
        }
    }

    private int createShaderProgram(String vertSrc, String fragSrc) {
        int vs = compileShader(GL_VERTEX_SHADER, vertSrc);
        int fs = compileShader(GL_FRAGMENT_SHADER, fragSrc);
        int prog = glCreateProgram();
        glAttachShader(prog, vs);
        glAttachShader(prog, fs);
        glLinkProgram(prog);
        if (glGetProgrami(prog, GL_LINK_STATUS) == 0) {
            throw new RuntimeException("Shader link error: " + glGetProgramInfoLog(prog));
        }
        glDeleteShader(vs);
        glDeleteShader(fs);
        return prog;
    }

    private int createComputeProgram(String computeSrc) {
        int cs = compileShader(GL_COMPUTE_SHADER, computeSrc);
        int prog = glCreateProgram();
        glAttachShader(prog, cs);
        glLinkProgram(prog);
        if (glGetProgrami(prog, GL_LINK_STATUS) == 0) {
            throw new RuntimeException("Compute shader link error: " + glGetProgramInfoLog(prog));
        }
        glDeleteShader(cs);
        return prog;
    }

    private int compileShader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
            throw new RuntimeException("Shader compile error: " + glGetShaderInfoLog(shader));
        }
        return shader;
    }

    private void cleanup() {
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    public static void main(String[] args) {
        new BlackHoleSim().run();
    }
}