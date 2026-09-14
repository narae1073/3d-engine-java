import imgui.ImGui;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import imgui.type.ImBoolean;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Engine3DLWJGL {
    private long window;
    private int width = 1280;
    private int height = 720;

    // [ImGui] 바인딩 객체
    private final ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();
    private final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();

    // 슬라임 애니메이션 변수
    private float scaleX = 1.0f, scaleY = 1.0f, scaleZ = 1.0f;
    private boolean wasGrounded = true;

    // 카메라 원근/직교 전환 및 부드러운 보간
    private final ImBoolean isOrthographic = new ImBoolean(false);
    private float orthoTransition = 0.0f;

    // 3D positional data
    private final Vector3f playerPos = new Vector3f(0.0f, 0.5f, 0.0f);
    private final Vector3f smoothCamPos = new Vector3f(0.0f, 0.8f, 0.0f);
    private float playerYaw = 0.0f;
    private final float playerSize = 1.0f;

    // Physics parameters (ImGui로 조절 가능)
    private float velocityY = 0.0f;
    private float gravity = -0.015f;
    private float jumpStrength = 0.32f;
    private boolean isGrounded = false;

    // Camera
    private float camYaw = 0.0f, camPitch = 0.2f;
    private float camDistance = 5.0f;

    // Light parameters (ImGui로 조절 가능)
    private final Vector3f lightPos = new Vector3f(5.0f, 10.0f, 5.0f);
    private float lightAmbient = 0.35f;
    private float lightDiffuse = 0.8f;

    // Controls & State
    private boolean w, a, s, d, space;
    private double lastMouseX = -1, lastMouseY = -1;
    private boolean isUiMode = false;

    // Matrix buffers & Matrices
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);
    private final Matrix4f projMatrix = new Matrix4f();
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f lightProjMatrix = new Matrix4f();
    private final Matrix4f lightViewMatrix = new Matrix4f();
    private final Matrix4f lightSpaceMatrix = new Matrix4f();
    private final Matrix4f modelMatrix = new Matrix4f();

    private final List<Block> blocks = new ArrayList<>();

    // 마우스 델타 누적 변수
    private double mouseDeltaX = 0;
    private double mouseDeltaY = 0;

    // 객체 재사용 (GC 억제)
    private final Vector3f moveDir = new Vector3f();
    private final Vector3f targetCam = new Vector3f();

    // Shadow Map (FBO)
    private final int SHADOW_WIDTH = 2048;
    private final int SHADOW_HEIGHT = 2048;
    private int depthFBO;
    private int depthMap;

    // Shader Programs
    private int mainShaderProgram;
    private int depthShaderProgram;

    // Shader Uniform Locations
    private int locModel, locView, locProj, locLightSpaceMatrix;
    private int locLightPos, locLightAmbient, locLightDiffuse, locObjectColor, locUseLighting, locShadowMap;

    // Geometry data
    private final float[][] vertices = {
            { -0.5f, -0.5f, -0.5f }, { 0.5f, -0.5f, -0.5f }, { 0.5f, 0.5f, -0.5f }, { -0.5f, 0.5f, -0.5f },
            { -0.5f, -0.5f, 0.5f }, { 0.5f, -0.5f, 0.5f }, { 0.5f, 0.5f, 0.5f }, { -0.5f, 0.5f, 0.5f }
    };
    private final int[][] faces = {
            { 4, 5, 6, 7 }, { 1, 0, 3, 2 }, { 3, 2, 6, 7 },
            { 4, 0, 1, 5 }, { 5, 1, 2, 6 }, { 0, 4, 7, 3 }
    };
    private final float[][] normals = {
            { 0, 0, 1 }, { 0, 0, -1 }, { 0, 1, 0 }, { 0, -1, 0 }, { 1, 0, 0 }, { -1, 0, 0 }
    };

    static class Block {
        Vector3f pos, size;

        public Block(float x, float y, float z, float sx, float sy, float sz) {
            this.pos = new Vector3f(x, y, z);
            this.size = new Vector3f(sx, sy, sz);
        }

        float minX() { return pos.x - size.x / 2.0f; }
        float maxX() { return pos.x + size.x / 2.0f; }
        float minY() { return pos.y - size.y / 2.0f; }
        float maxY() { return pos.y + size.y / 2.0f; }
        float minZ() { return pos.z - size.z / 2.0f; }
        float maxZ() { return pos.z + size.z / 2.0f; }
    }

    public void run() {
        init();
        loop();
        cleanup();
    }

    private void init() {
        if (!glfwInit())
            throw new IllegalStateException("GLFW 초기화 실패");

        window = glfwCreateWindow(width, height, "3D Engine - Shadows & Slime Integrated", NULL, NULL);

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        GL.createCapabilities();

        // ImGui 초기화
        ImGui.createContext();
        imGuiGlfw.init(window, true);
        imGuiGl3.init("#version 120");

        // 콜백 설정
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            boolean isPressed = (action != GLFW_RELEASE);
            if (key == GLFW_KEY_W) w = isPressed;
            if (key == GLFW_KEY_A) a = isPressed;
            if (key == GLFW_KEY_S) s = isPressed;
            if (key == GLFW_KEY_D) d = isPressed;
            if (key == GLFW_KEY_SPACE) space = isPressed;

            if (key == GLFW_KEY_TAB && action == GLFW_PRESS) {
                isUiMode = !isUiMode;
                glfwSetInputMode(window, GLFW_CURSOR, isUiMode ? GLFW_CURSOR_NORMAL : GLFW_CURSOR_DISABLED);
                lastMouseX = -1;
                lastMouseY = -1;
            }
        });

        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            if (lastMouseX == -1) {
                lastMouseX = xpos;
                lastMouseY = ypos;
            }

            if (!isUiMode) {
                mouseDeltaX += xpos - lastMouseX;
                mouseDeltaY += ypos - lastMouseY;
            }

            lastMouseX = xpos;
            lastMouseY = ypos;
        });

        glEnable(GL_DEPTH_TEST);

        if (glfwRawMouseMotionSupported()) {
            glfwSetInputMode(window, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE);
        }

        blocks.add(new Block(2.0f, 0.5f, -3.0f, 2.0f, 1.0f, 2.0f));
        blocks.add(new Block(2.0f, 1.25f, -6.0f, 2.0f, 2.5f, 2.0f));
        blocks.add(new Block(-2.5f, 1.0f, -4.0f, 2.0f, 2.0f, 2.0f));

        smoothCamPos.set(playerPos.x, playerPos.y + 0.3f, playerPos.z);

        initShadowFBO();
        initShaders();
    }

    private void initShadowFBO() {
        depthFBO = glGenFramebuffers();
        depthMap = glGenTextures();

        glBindTexture(GL_TEXTURE_2D, depthMap);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT, SHADOW_WIDTH, SHADOW_HEIGHT, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (FloatBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        float[] borderColor = { 1.0f, 1.0f, 1.0f, 1.0f };
        glTexParameterfv(GL_TEXTURE_2D, GL_TEXTURE_BORDER_COLOR, borderColor);

        glBindFramebuffer(GL_FRAMEBUFFER, depthFBO);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthMap, 0);
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    private void initShaders() {
        // 1. Depth Shader (Shadow Map Pass)
        String depthVS = "#version 120\n" +
                "uniform mat4 lightSpaceMatrix;\n" +
                "uniform mat4 model;\n" +
                "void main() {\n" +
                "    gl_Position = lightSpaceMatrix * model * gl_Vertex;\n" +
                "}\n";
        String depthFS = "#version 120\n" +
                "void main() {\n" +
                "}\n";

        depthShaderProgram = createProgram(depthVS, depthFS);

        // 2. Main Shader (Camera Pass + Shadow Map Processing with 3x3 PCF)
        String mainVS = "#version 120\n" +
                "uniform mat4 model;\n" +
                "uniform mat4 view;\n" +
                "uniform mat4 proj;\n" +
                "uniform mat4 lightSpaceMatrix;\n" +
                "varying vec3 FragPos;\n" +
                "varying vec3 Normal;\n" +
                "varying vec4 FragPosLightSpace;\n" +
                "void main() {\n" +
                "    FragPos = vec3(model * gl_Vertex);\n" +
                "    Normal = mat3(model) * gl_Normal;\n" +
                "    FragPosLightSpace = lightSpaceMatrix * vec4(FragPos, 1.0);\n" +
                "    gl_Position = proj * view * vec4(FragPos, 1.0);\n" +
                "}\n";

        String mainFS = "#version 120\n" +
                "varying vec3 FragPos;\n" +
                "varying vec3 Normal;\n" +
                "varying vec4 FragPosLightSpace;\n" +
                "uniform vec3 lightPos;\n" +
                "uniform float lightAmbient;\n" +
                "uniform float lightDiffuse;\n" +
                "uniform vec3 objectColor;\n" +
                "uniform bool useLighting;\n" +
                "uniform sampler2D shadowMap;\n" +
                "float ShadowCalculation(vec4 fragPosLightSpace, vec3 normal, vec3 lightDir) {\n" +
                "    vec3 projCoords = fragPosLightSpace.xyz / fragPosLightSpace.w;\n" +
                "    projCoords = projCoords * 0.5 + 0.5;\n" +
                "    if(projCoords.z > 1.0) return 0.0;\n" +
                "    float bias = max(0.001 * (1.0 - dot(normal, lightDir)), 0.0005);\n" +
                "    float shadow = 0.0;\n" +
                "    vec2 texelSize = vec2(1.0 / 2048.0);\n" +
                "    for(int x = -1; x <= 1; ++x) {\n" +
                "        for(int y = -1; y <= 1; ++y) {\n" +
                "            float pcfDepth = texture2D(shadowMap, projCoords.xy + vec2(x, y) * texelSize).r;\n" +
                "            shadow += projCoords.z - bias > pcfDepth ? 1.0 : 0.0;\n" +
                "        }\n" +
                "    }\n" +
                "    shadow /= 9.0;\n" +
                "    return shadow;\n" +
                "}\n" +
                "void main() {\n" +
                "    if (!useLighting) {\n" +
                "        gl_FragColor = vec4(objectColor, 1.0);\n" +
                "        return;\n" +
                "    }\n" +
                "    vec3 norm = normalize(Normal);\n" +
                "    vec3 lightDir = normalize(lightPos - FragPos);\n" +
                "    float diff = max(dot(norm, lightDir), 0.0);\n" +
                "    vec3 ambient = lightAmbient * objectColor;\n" +
                "    vec3 diffuse = diff * lightDiffuse * objectColor;\n" +
                "    float shadow = ShadowCalculation(FragPosLightSpace, norm, lightDir);\n" +
                "    vec3 result = ambient + (1.0 - shadow) * diffuse;\n" +
                "    gl_FragColor = vec4(result, 1.0);\n" +
                "}\n";

        mainShaderProgram = createProgram(mainVS, mainFS);

        // Uniform Locations
        locModel = glGetUniformLocation(mainShaderProgram, "model");
        locView = glGetUniformLocation(mainShaderProgram, "view");
        locProj = glGetUniformLocation(mainShaderProgram, "proj");
        locLightSpaceMatrix = glGetUniformLocation(mainShaderProgram, "lightSpaceMatrix");
        locLightPos = glGetUniformLocation(mainShaderProgram, "lightPos");
        locLightAmbient = glGetUniformLocation(mainShaderProgram, "lightAmbient");
        locLightDiffuse = glGetUniformLocation(mainShaderProgram, "lightDiffuse");
        locObjectColor = glGetUniformLocation(mainShaderProgram, "objectColor");
        locUseLighting = glGetUniformLocation(mainShaderProgram, "useLighting");
        locShadowMap = glGetUniformLocation(mainShaderProgram, "shadowMap");
    }

    private int createProgram(String vCode, String fCode) {
        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, vCode);
        glCompileShader(vs);

        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, fCode);
        glCompileShader(fs);

        int prog = glCreateProgram();
        glAttachShader(prog, vs);
        glAttachShader(prog, fs);
        glLinkProgram(prog);

        glDeleteShader(vs);
        glDeleteShader(fs);
        return prog;
    }

    private void update() {
        if (!isUiMode) {
            float sensitivity = 0.0025f;
            camYaw += (float) mouseDeltaX * sensitivity;
            camPitch += (float) mouseDeltaY * sensitivity;
            camPitch = Math.max((float) Math.toRadians(-10), Math.min((float) Math.toRadians(85), camPitch));

            mouseDeltaX = 0;
            mouseDeltaY = 0;

            float speed = 0.08f;
            moveDir.set(0, 0, 0);

            if (w) { moveDir.x += Math.sin(camYaw); moveDir.z -= Math.cos(camYaw); }
            if (s) { moveDir.x -= Math.sin(camYaw); moveDir.z += Math.cos(camYaw); }
            if (a) { moveDir.x -= Math.cos(camYaw); moveDir.z -= Math.sin(camYaw); }
            if (d) { moveDir.x += Math.cos(camYaw); moveDir.z += Math.sin(camYaw); }

            if (moveDir.lengthSquared() > 0) {
                moveDir.normalize().mul(speed);
                playerPos.x += moveDir.x; resolveHorizontalCollision(true, moveDir.x);
                playerPos.z += moveDir.z; resolveHorizontalCollision(false, moveDir.z);
                playerYaw = (float) Math.atan2(moveDir.x, -moveDir.z);
            }

            // 슬라임 스케일 스프링 복구 보간
            scaleX += (1.0f - scaleX) * 0.15f;
            scaleY += (1.0f - scaleY) * 0.15f;
            scaleZ += (1.0f - scaleZ) * 0.15f;

            velocityY += gravity;
            playerPos.y += velocityY;
            isGrounded = false;

            if (playerPos.y <= 0.5f) { playerPos.y = 0.5f; velocityY = 0; isGrounded = true; }

            for (Block b : blocks) {
                if (checkAABBOverlap(playerPos, b)) {
                    if (velocityY < 0) { playerPos.y = b.maxY() + playerSize / 2.0f; velocityY = 0; isGrounded = true; }
                    else if (velocityY > 0) { playerPos.y = b.minY() - playerSize / 2.0f; velocityY = 0; }
                }
            }

            if (space && isGrounded) { 
                velocityY = jumpStrength; 
                isGrounded = false; 
                scaleY = 1.4f;
                scaleX = 0.7f;
                scaleZ = 0.7f;
            }

            // 착지 순간 감지: 바닥에 닿는 순간 납작하게 찌그러짐
            boolean justLanded = !wasGrounded && isGrounded;
            if (justLanded) {
                scaleY = 0.6f;
                scaleX = 1.3f;
                scaleZ = 1.3f;
            }

            wasGrounded = isGrounded;
        }

        // 투영 전환 애니메이션 보간
        float targetTransition = isOrthographic.get() ? 1.0f : 0.0f;
        orthoTransition += (targetTransition - orthoTransition) * 0.12f;

        targetCam.set(playerPos.x, playerPos.y + 0.3f, playerPos.z);
        smoothCamPos.lerp(targetCam, 0.15f);
    }

    private void render() {
        // --- 1. Light Space Matrix 계산 (Texel Snap 적용) ---
        float nearPlane = 1.0f, farPlane = 40.0f;
        lightProjMatrix.identity().setOrtho(-15.0f, 15.0f, -15.0f, 15.0f, nearPlane, farPlane);

        Vector3f lightTarget = new Vector3f(playerPos.x, 0.0f, playerPos.z);
        Vector3f actualLightPos = new Vector3f(playerPos.x + lightPos.x, lightPos.y, playerPos.z + lightPos.z);
        lightViewMatrix.identity().lookAt(actualLightPos, lightTarget, new Vector3f(0.0f, 1.0f, 0.0f));

        lightProjMatrix.mul(lightViewMatrix, lightSpaceMatrix);

        // Texel Snapping: 그림자 떨림(Shimmering) 방지
        Matrix4f shadowMatrix = new Matrix4f(lightSpaceMatrix);
        org.joml.Vector4f shadowOrigin = new org.joml.Vector4f(0.0f, 0.0f, 0.0f, 1.0f).mul(shadowMatrix);
        shadowOrigin.mul(SHADOW_WIDTH / 2.0f);

        float roundedX = Math.round(shadowOrigin.x);
        float roundedY = Math.round(shadowOrigin.y);
        float dx = (roundedX - shadowOrigin.x) * (2.0f / SHADOW_WIDTH);
        float dy = (roundedY - shadowOrigin.y) * (2.0f / SHADOW_HEIGHT);

        Matrix4f roundMatrix = new Matrix4f().translate(dx, dy, 0.0f);
        roundMatrix.mul(lightSpaceMatrix, lightSpaceMatrix);

        // --- PASS 1: Render Shadow Map (Depth FBO) ---
        glViewport(0, 0, SHADOW_WIDTH, SHADOW_HEIGHT);
        glBindFramebuffer(GL_FRAMEBUFFER, depthFBO);
        glClear(GL_DEPTH_BUFFER_BIT);

        glUseProgram(depthShaderProgram);
        int locDepthLightSpace = glGetUniformLocation(depthShaderProgram, "lightSpaceMatrix");
        int locDepthModel = glGetUniformLocation(depthShaderProgram, "model");

        glUniformMatrix4fv(locDepthLightSpace, false, lightSpaceMatrix.get(matrixBuffer));

        // 앞면 컬링 적용으로 그림자 아크네(줄무늬) 방지
        glEnable(GL_CULL_FACE);
        glCullFace(GL_FRONT);
        renderScene(locDepthModel, false);
        glCullFace(GL_BACK);
        glDisable(GL_CULL_FACE);

        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        // --- PASS 2: Render Normal Scene ---
        glViewport(0, 0, width, height);
        glClearColor(0.53f, 0.81f, 0.92f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glUseProgram(mainShaderProgram);

        // Perspective / Orthographic 부드러운 전환 보간
        Matrix4f perspProj = new Matrix4f().setPerspective((float) Math.toRadians(60.0f), (float) width / height, 0.1f, 100.0f);
        float orthoSize = 6.0f;
        Matrix4f orthoProj = new Matrix4f().setOrtho(-orthoSize * ((float) width / height), orthoSize * ((float) width / height), -orthoSize, orthoSize, 0.1f, 100.0f);
        projMatrix.set(perspProj).lerp(orthoProj, orthoTransition);

        viewMatrix.identity()
                .translate(0, 0, -camDistance)
                .rotateX(camPitch)
                .rotateY(camYaw)
                .translate(-smoothCamPos.x, -smoothCamPos.y, -smoothCamPos.z);

        glUniformMatrix4fv(locProj, false, projMatrix.get(matrixBuffer));
        glUniformMatrix4fv(locView, false, viewMatrix.get(matrixBuffer));
        glUniformMatrix4fv(locLightSpaceMatrix, false, lightSpaceMatrix.get(matrixBuffer));

        // Light Settings
        glUniform3f(locLightPos, lightPos.x, lightPos.y, lightPos.z);
        glUniform1f(locLightAmbient, lightAmbient);
        glUniform1f(locLightDiffuse, lightDiffuse);

        // Bind Shadow Map Texture
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, depthMap);
        glUniform1i(locShadowMap, 0);

        renderScene(locModel, true);

        glUseProgram(0);

        renderImGui();
    }

    private void renderScene(int modelLoc, boolean isMainPass) {
        // 1. Ground Render
        modelMatrix.identity();
        glUniformMatrix4fv(modelLoc, false, modelMatrix.get(matrixBuffer));

        if (isMainPass) {
            glUniform3f(locObjectColor, 0.5f, 0.5f, 0.5f);
            glUniform1i(locUseLighting, 1);
        }
        drawGroundGeometry();

        if (isMainPass) {
            // Grid Lines (Lighting 꺼짐)
            glUniform3f(locObjectColor, 0.0f, 0.0f, 0.0f);
            glUniform1i(locUseLighting, 0);
            drawGridGeometry();
            glUniform1i(locUseLighting, 1);
        }

        // 2. Obstacle Blocks Render
        for (Block b : blocks) {
            modelMatrix.identity()
                    .translate(b.pos.x, b.pos.y, b.pos.z)
                    .scale(b.size.x, b.size.y, b.size.z);
            glUniformMatrix4fv(modelLoc, false, modelMatrix.get(matrixBuffer));

            if (isMainPass) {
                glUniform3f(locObjectColor, 0.95f, 0.5f, 0.2f);
            }
            drawCubeGeometry();
        }

        // 3. Player Cube Render (슬라임 스케일 애니메이션 적용)
        modelMatrix.identity()
                .translate(playerPos.x, playerPos.y, playerPos.z)
                .rotateY(playerYaw)
                .scale(playerSize * scaleX, playerSize * scaleY, playerSize * scaleZ);
        glUniformMatrix4fv(modelLoc, false, modelMatrix.get(matrixBuffer));

        if (isMainPass) {
            if (isGrounded) glUniform3f(locObjectColor, 0.2f, 0.9f, 1.0f);
            else glUniform3f(locObjectColor, 0.1f, 0.6f, 1.0f);
        }
        drawCubeGeometry();
    }

    private void renderImGui() {
        imGuiGlfw.newFrame();
        ImGui.newFrame();

        ImGui.begin("Engine Control Panel");

        float fps = ImGui.getIO().getFramerate();
        float frameTime = 1000.0f / (fps > 0 ? fps : 1.0f);

        ImGui.text(String.format("FPS: %.1f", fps));
        ImGui.text(String.format("Frame Time: %.2f ms", frameTime));
        ImGui.separator();

        ImGui.text("Press TAB to toggle Cursor & UI Control");
        ImGui.separator();

        // 원근/직교 전환 체크박스
        if (ImGui.checkbox("Orthographic Mode", isOrthographic)) {
            // 토글 이벤트 시 추가 처리 필요하면 여기에 작성
        }

        ImGui.separator();

        float[] lp = { lightPos.x, lightPos.y, lightPos.z };
        if (ImGui.sliderFloat3("Light Position", lp, -20.0f, 20.0f)) {
            lightPos.set(lp[0], lp[1], lp[2]);
        }

        float[] la = { lightAmbient };
        if (ImGui.sliderFloat("Ambient Light", la, 0.0f, 1.0f)) {
            lightAmbient = la[0];
        }

        float[] ld = { lightDiffuse };
        if (ImGui.sliderFloat("Diffuse Light", ld, 0.0f, 1.0f)) {
            lightDiffuse = ld[0];
        }

        ImGui.separator();

        float[] jp = { jumpStrength };
        if (ImGui.sliderFloat("Jump Strength", jp, 0.1f, 0.8f)) {
            jumpStrength = jp[0];
        }

        float[] gr = { gravity };
        if (ImGui.sliderFloat("Gravity", gr, -0.05f, -0.001f)) {
            gravity = gr[0];
        }

        float[] cd = { camDistance };
        if (ImGui.sliderFloat("Camera Distance", cd, 2.0f, 15.0f)) {
            camDistance = cd[0];
        }

        ImGui.end();

        ImGui.render();
        imGuiGl3.renderDrawData(ImGui.getDrawData());
    }

    private void drawCubeGeometry() {
        glBegin(GL_QUADS);
        for (int i = 0; i < faces.length; i++) {
            glNormal3f(normals[i][0], normals[i][1], normals[i][2]);
            for (int vertIndex : faces[i]) {
                glVertex3f(vertices[vertIndex][0], vertices[vertIndex][1], vertices[vertIndex][2]);
            }
        }
        glEnd();
    }

    private void drawGroundGeometry() {
        glBegin(GL_QUADS);
            glNormal3f(0, 1, 0);
            glVertex3f(-30.0f, 0.0f, -30.0f);
            glVertex3f( 30.0f, 0.0f, -30.0f);
            glVertex3f( 30.0f, 0.0f,  30.0f);
            glVertex3f(-30.0f, 0.0f,  30.0f);
        glEnd();
    }

    private void drawGridGeometry() {
        glBegin(GL_LINES);
        for (int i = -30; i <= 30; i++) {
            glVertex3f(-30, 0.001f, i); glVertex3f(30, 0.001f, i);
            glVertex3f(i, 0.001f, -30); glVertex3f(i, 0.001f, 30);
        }
        glEnd();
    }

    private void resolveHorizontalCollision(boolean isX, float moveDir) {
        for (Block b : blocks) {
            if (checkAABBOverlap(playerPos, b)) {
                if (isX) playerPos.x = moveDir > 0 ? b.minX() - playerSize / 2.0f : b.maxX() + playerSize / 2.0f;
                else playerPos.z = moveDir > 0 ? b.minZ() - playerSize / 2.0f : b.maxZ() + playerSize / 2.0f;
            }
        }
    }

    private boolean checkAABBOverlap(Vector3f p, Block b) {
        float r = playerSize / 2.0f;
        return (p.x + r > b.minX() && p.x - r < b.maxX()) &&
                (p.y + r > b.minY() && p.y - r < b.maxY()) &&
                (p.z + r > b.minZ() && p.z - r < b.maxZ());
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            update();
            render();
            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void cleanup() {
        glDeleteFramebuffers(depthFBO);
        glDeleteTextures(depthMap);
        glDeleteProgram(mainShaderProgram);
        glDeleteProgram(depthShaderProgram);

        imGuiGl3.dispose();
        imGuiGlfw.dispose();
        ImGui.destroyContext();

        glfwDestroyWindow(window);
        glfwTerminate();
    }

    public static void main(String[] args) {
        new Engine3DLWJGL().run();
    }
}