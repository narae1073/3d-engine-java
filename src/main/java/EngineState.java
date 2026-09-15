import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import imgui.type.ImBoolean;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class EngineState {
    public long window;
    public int width = 1920;
    public int height = 1080;

    // depth pass uniform locations (render 루프에서 캐싱, 매 프레임 조회 금지)
    public int locDepthLightSpace, locDepthModel;

    public boolean isLocalGizmo = true;

    // [ImGui] binding object
    public final ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();
    public final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();

    // Slime animation vars
    public float scaleX = 1.0f, scaleY = 1.0f, scaleZ = 1.0f;
    public boolean wasGrounded = true;

    // Camera perspective / orthographic transition
    public final ImBoolean isOrthographic = new ImBoolean(false);
    public float orthoTransition = 0.0f;

    // Editor mode
    public final ImBoolean isEditorMode = new ImBoolean(false);

    // -1: no selected object, >=0: LevelObject selected
    public int selectedObjectIndex = -1;

    // Editor free camera
    public final Vector3f editorCamPos = new Vector3f();
    public float editorCamYaw = 0.0f;
    public float editorCamPitch = 0.0f;
    public boolean hasInitializedEditorCam = false;

    // Editor camera speed multiplier
    public float editorSpeed = 0.2f;
    public float editorSpeedMultiplier = 2.5f;

    // object picking
    public boolean prevMousePressed = false;
    public final Matrix4f editorInvVPMatrix = new Matrix4f();

    // unified object list and player quick reference
    public final List<LevelObject> objects = new ArrayList<>();
    public PlayerObject playerObject;

    // camera and physics params
    public final Vector3f smoothCamPos = new Vector3f(0.0f, 0.8f, 0.0f);
    public float velocityY = 0.0f;
    public float gravity = -0.015f;
    public float jumpStrength = 0.32f;
    public boolean isGrounded = false;

    public float camYaw = 0.0f, camPitch = 0.2f;
    public float camDistance = 5.0f;

    // light params
    public final Vector3f lightPos = new Vector3f(5.0f, 10.0f, 5.0f);
    public float lightAmbient = 0.35f;
    public float lightDiffuse = 0.8f;

    // Controls & state
    public boolean w, a, s, d, space;
    public double lastMouseX = -1, lastMouseY = -1;
    public boolean isUiMode = false;

    // Matrix buffers & matrices
    public final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);
    public final Matrix4f projMatrix = new Matrix4f();
    public final Matrix4f viewMatrix = new Matrix4f();
    public final Matrix4f lightProjMatrix = new Matrix4f();
    public final Matrix4f lightViewMatrix = new Matrix4f();
    public final Matrix4f lightSpaceMatrix = new Matrix4f();
    public final Matrix4f modelMatrix = new Matrix4f();

    // mouse delta accumulator
    public double mouseDeltaX = 0;
    public double mouseDeltaY = 0;

    // object reuse
    public final Vector3f moveDir = new Vector3f();
    public final Vector3f targetCam = new Vector3f();

    // Shadow map (FBO)
    public final int SHADOW_WIDTH = 2048;
    public final int SHADOW_HEIGHT = 2048;
    public int depthFBO;
    public int depthMap;

    // shader programs
    public int mainShaderProgram;
    public int depthShaderProgram;

    // shader uniform locations
    public int locModel, locView, locProj, locLightSpaceMatrix;
    public int locLightPos, locLightAmbient, locLightDiffuse, locObjectColor, locUseLighting, locShadowMap;

    // Geometry data
    public final float[][] vertices = {
            { -0.5f, -0.5f, -0.5f }, { 0.5f, -0.5f, -0.5f }, { 0.5f, 0.5f, -0.5f }, { -0.5f, 0.5f, -0.5f },
            { -0.5f, -0.5f, 0.5f }, { 0.5f, -0.5f, 0.5f }, { 0.5f, 0.5f, 0.5f }, { -0.5f, 0.5f, 0.5f }
    };
    public final int[][] faces = {
            { 4, 5, 6, 7 }, { 1, 0, 3, 2 }, { 3, 2, 6, 7 },
            { 4, 0, 1, 5 }, { 5, 1, 2, 6 }, { 0, 4, 7, 3 }
    };
    public final float[][] normals = {
            { 0, 0, 1 }, { 0, 0, -1 }, { 0, 1, 0 }, { 0, -1, 0 }, { 1, 0, 0 }, { -1, 0, 0 }
    };

    public int activeGizmoAxis = 0; // 0: 없음, 1: X축, 2: Y축, 3: Z축

    // 현재 기즈모가 어떤 조작을 담당하는지 나타내는 변수
    public int gizmoMode = 0; // 0: 위치(Position), 1: 크기(Scale), 2: 회전(Rotation)
}
