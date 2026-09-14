import imgui.ImGui;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
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
import static org.lwjgl.system.MemoryUtil.NULL;

public class Engine3DLWJGL {
    private long window;
    private int width = 1280;
    private int height = 720;

    // [ImGui] 바인딩 객체
    private final ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();
    private final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();

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
    private float lightAmbient = 0.3f;
    private float lightDiffuse = 0.8f;

    // Controls & State
    private boolean w, a, s, d, space;
    private double lastMouseX = -1, lastMouseY = -1;
    private boolean isUiMode = false;

    // Matrix buffers
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);
    private final Matrix4f projMatrix = new Matrix4f();
    private final Matrix4f viewMatrix = new Matrix4f();

    private final List<Block> blocks = new ArrayList<>();

    // 마우스 델타 누적 변수
    private double mouseDeltaX = 0;
    private double mouseDeltaY = 0;

    // 객체 재사용 (GC 억제)
    private final Vector3f moveDir = new Vector3f();
    private final Vector3f targetCam = new Vector3f();

    // Geometry data
    private final float[][] vertices = {
            {-0.5f, -0.5f, -0.5f}, {0.5f, -0.5f, -0.5f}, {0.5f, 0.5f, -0.5f}, {-0.5f, 0.5f, -0.5f},
            {-0.5f, -0.5f, 0.5f},  {0.5f, -0.5f, 0.5f},  {0.5f, 0.5f, 0.5f},  {-0.5f, 0.5f, 0.5f}
    };
    private final int[][] faces = {
            {4, 5, 6, 7}, {1, 0, 3, 2}, {3, 2, 6, 7},
            {4, 0, 1, 5}, {5, 1, 2, 6}, {0, 4, 7, 3}
    };
    private final float[][] normals = {
            {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}
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
        if (!glfwInit()) throw new IllegalStateException("GLFW 초기화 실패");

        // 스텐실 버퍼 8비트 요청 (그림자 중복 투영 방지)
        glfwWindowHint(GLFW_STENCIL_BITS, 8);

        window = glfwCreateWindow(width, height, "3D Engine - ImGui Integrated", NULL, NULL);

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
                lastMouseX = -1; lastMouseY = -1;
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
        glEnable(GL_LIGHTING);
        glEnable(GL_LIGHT0);
        glEnable(GL_COLOR_MATERIAL);
        glEnable(GL_NORMALIZE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        if (glfwRawMouseMotionSupported()) {
            glfwSetInputMode(window, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE);
        }

        blocks.add(new Block(2.0f, 0.5f, -3.0f, 2.0f, 1.0f, 2.0f));
        blocks.add(new Block(2.0f, 1.25f, -6.0f, 2.0f, 2.5f, 2.0f));
        blocks.add(new Block(-2.5f, 1.0f, -4.0f, 2.0f, 2.0f, 2.0f));

        smoothCamPos.set(playerPos.x, playerPos.y + 0.3f, playerPos.z);
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

            if (space && isGrounded) { velocityY = jumpStrength; isGrounded = false; }
        }

        targetCam.set(playerPos.x, playerPos.y + 0.3f, playerPos.z);
        smoothCamPos.lerp(targetCam, 0.15f);
    }

    private void render() {
        // 지정된 하늘 색상 적용
        glClearColor(0.53f, 0.81f, 0.92f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);

        projMatrix.identity().setPerspective((float) Math.toRadians(60.0f), (float) width / height, 0.1f, 100.0f);
        glMatrixMode(GL_PROJECTION);
        glLoadMatrixf(projMatrix.get(matrixBuffer));

        viewMatrix.identity()
                .translate(0, 0, -camDistance)
                .rotateX(camPitch)
                .rotateY(camYaw)
                .translate(-smoothCamPos.x, -smoothCamPos.y, -smoothCamPos.z);
        glMatrixMode(GL_MODELVIEW);
        glLoadMatrixf(viewMatrix.get(matrixBuffer));

        // Light config
        float[] lPos = {lightPos.x, lightPos.y, lightPos.z, 0.0f};
        float[] lAmb = {lightAmbient, lightAmbient, lightAmbient, 1.0f};
        float[] lDiff = {lightDiffuse, lightDiffuse, lightDiffuse, 1.0f};
        glLightfv(GL_LIGHT0, GL_POSITION, lPos);
        glLightfv(GL_LIGHT0, GL_AMBIENT, lAmb);
        glLightfv(GL_LIGHT0, GL_DIFFUSE, lDiff);

        // Render World
        glDisable(GL_LIGHTING);
        drawGround();
        drawPlanarShadows();

        glEnable(GL_LIGHTING);
        for (Block b : blocks) drawBlock(b);

        glPushMatrix();
        glTranslatef(playerPos.x, playerPos.y, playerPos.z);
        glRotatef((float) Math.toDegrees(playerYaw), 0, 1, 0);
        glColor3f(isGrounded ? 0.2f : 0.1f, isGrounded ? 0.9f : 0.6f, 1.0f);
        drawCubeGeometry();
        glPopMatrix();

        renderImGui();
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

        float[] lp = {lightPos.x, lightPos.y, lightPos.z};
        if (ImGui.sliderFloat3("Light Position", lp, -20.0f, 20.0f)) {
            lightPos.set(lp[0], lp[1], lp[2]);
        }

        float[] la = {lightAmbient};
        if (ImGui.sliderFloat("Ambient Light", la, 0.0f, 1.0f)) {
            lightAmbient = la[0];
        }

        float[] ld = {lightDiffuse};
        if (ImGui.sliderFloat("Diffuse Light", ld, 0.0f, 1.0f)) {
            lightDiffuse = ld[0];
        }

        ImGui.separator();

        float[] jp = {jumpStrength};
        if (ImGui.sliderFloat("Jump Strength", jp, 0.1f, 0.8f)) {
            jumpStrength = jp[0];
        }

        float[] gr = {gravity};
        if (ImGui.sliderFloat("Gravity", gr, -0.05f, -0.001f)) {
            gravity = gr[0];
        }

        float[] cd = {camDistance};
        if (ImGui.sliderFloat("Camera Distance", cd, 2.0f, 15.0f)) {
            camDistance = cd[0];
        }

        ImGui.end();

        ImGui.render();
        imGuiGl3.renderDrawData(ImGui.getDrawData());
    }

    private void drawPlanarShadows() {
        glDisable(GL_LIGHTING);
        glDepthMask(false);

        glEnable(GL_STENCIL_TEST);
        glClear(GL_STENCIL_BUFFER_BIT);
        glStencilFunc(GL_EQUAL, 0, 0xFF);
        glStencilOp(GL_KEEP, GL_KEEP, GL_INCR);

        glColor4f(0.0f, 0.0f, 0.0f, 0.35f);

        drawCubeShadow(playerPos.x, playerPos.y, playerPos.z, playerSize, playerSize, playerSize, playerYaw);
        for (Block b : blocks) {
            drawCubeShadow(b.pos.x, b.pos.y, b.pos.z, b.size.x, b.size.y, b.size.z, 0.0f);
        }

        glDisable(GL_STENCIL_TEST);
        glDepthMask(true);
    }

    private void drawCubeShadow(float cx, float cy, float cz, float sx, float sy, float sz, float yaw) {
        glPushMatrix();
        glBegin(GL_QUADS);
        float ly = Math.max(0.1f, lightPos.y);

        for (int[] face : faces) {
            for (int vertIndex : face) {
                float[] v = vertices[vertIndex];
                float vx = v[0] * sx, vy = v[1] * sy, vz = v[2] * sz;

                if (yaw != 0) {
                    // OpenGL 회전 연산방식(glRotatef)에 동기화되도록 회전 수식 부호 수정
                    float rx = (float) (vx * Math.cos(yaw) + vz * Math.sin(yaw));
                    float rz = (float) (-vx * Math.sin(yaw) + vz * Math.cos(yaw));
                    vx = rx; vz = rz;
                }

                float worldX = cx + vx, worldY = cy + vy, worldZ = cz + vz;
                float projX = worldX - lightPos.x * (worldY / ly);
                float projZ = worldZ - lightPos.z * (worldY / ly);

                glVertex3f(projX, 0.001f, projZ);
            }
        }
        glEnd();
        glPopMatrix();
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

    private void drawBlock(Block b) {
        glPushMatrix();
        glTranslatef(b.pos.x, b.pos.y, b.pos.z);
        glScalef(b.size.x, b.size.y, b.size.z);
        glColor3f(0.95f, 0.5f, 0.2f);
        drawCubeGeometry();
        glPopMatrix();
    }

    private void drawGround() {
        // 지정된 바닥 색상 적용 (회색)
        glColor3f(0.5f, 0.5f, 0.5f);
        glBegin(GL_QUADS);
        glNormal3f(0, 1, 0);
        glVertex3f(-30.0f, 0.0f, -30.0f);
        glVertex3f( 30.0f, 0.0f, -30.0f);
        glVertex3f( 30.0f, 0.0f,  30.0f);
        glVertex3f(-30.0f, 0.0f,  30.0f);
        glEnd();

        // 바닥 격자 선 (검은색)
        glColor3f(0.0f, 0.0f, 0.0f);
        glBegin(GL_LINES);
        for (int i = -30; i <= 30; i++) {
            glVertex3f(-30, 0.0005f, i); glVertex3f(30, 0.0005f, i);
            glVertex3f(i, 0.0005f, -30); glVertex3f(i, 0.0005f, 30);
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