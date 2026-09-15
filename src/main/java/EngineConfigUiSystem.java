import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import imgui.type.ImInt;

/**
 * EngineConfig 실시간 튜닝 패널.
 *
 * <p>
 * EngineUiSystem이 소유하고, EngineUiSystem.render() 안에서 호출된다.
 * (ImGui 프레임은 EngineUiSystem이 단독 관리)
 */
public class EngineConfigUiSystem {

    // 파일 저장용 상태
    private final ImString savePath = new ImString("./engine.properties", 256);
    private String lastSaveMessage = "";
    private double lastSaveMessageTime = 0.0;

    /**
     * ImGui 창 하나를 그린다.
     * EngineUiSystem이 이미 newFrame()을 호출한 상태라고 가정.
     */
    public void render() {
        ImGui.setNextWindowSize(420, 720, ImGuiCond.FirstUseEver);
        ImGui.begin("Engine Config");

        // ============================================================
        // 상단: 파일 저장/리셋 컨트롤
        // ============================================================
        if (ImGui.collapsingHeader("File", ImGuiTreeNodeFlags.DefaultOpen)) {
            ImGui.inputText("##savePath", savePath);

            if (ImGui.button("Save")) {
                saveToFile();
            }
            ImGui.sameLine();
            if (ImGui.button("Reset to Code Defaults")) {
                resetToDefaults();
            }
            ImGui.sameLine();
            if (ImGui.button("Reload from File")) {
                reloadFromFile();
            }

            if (!lastSaveMessage.isEmpty()) {
                double now = ImGui.getTime();
                if (now - lastSaveMessageTime < 3.0) {
                    ImGui.sameLine();
                    ImGui.textColored(0.4f, 1.0f, 0.4f, 1.0f, lastSaveMessage);
                } else {
                    lastSaveMessage = "";
                }
            }

            ImGui.separator();
        }

        // ============================================================
        // Window (재시작 필요 항목 경고)
        // ============================================================
        if (ImGui.collapsingHeader("Window")) {
            // Width
            ImInt w = new ImInt(EngineConfig.Window.WIDTH);
            if (ImGui.inputInt("Width", w)) {
                EngineConfig.Window.WIDTH = w.get();
            }

            // Height
            ImInt h = new ImInt(EngineConfig.Window.HEIGHT);
            if (ImGui.inputInt("Height", h)) {
                EngineConfig.Window.HEIGHT = h.get();
            }
            ImGui.textDisabled("(width/height need restart)");

            ImString title = new ImString(EngineConfig.Window.TITLE, 128);
            if (ImGui.inputText("Title", title)) {
                EngineConfig.Window.TITLE = title.get();
            }

            int[] swap = { EngineConfig.Window.SWAP_INTERVAL };
            if (ImGui.sliderInt("Swap Interval", swap, 0, 4)) {
                EngineConfig.Window.SWAP_INTERVAL = swap[0];
            }
        }

        // ============================================================
        // Render
        // ============================================================
        if (ImGui.collapsingHeader("Render")) {
            // Shadow
            ImGui.text("Shadow");
            ImInt sw = new ImInt(EngineConfig.Render.SHADOW_WIDTH);
            if (ImGui.inputInt("Shadow Width", sw)) {
                EngineConfig.Render.SHADOW_WIDTH = Math.max(64, sw.get());
            }

            ImInt sh = new ImInt(EngineConfig.Render.SHADOW_HEIGHT);
            if (ImGui.inputInt("Shadow Height", sh)) {
                EngineConfig.Render.SHADOW_HEIGHT = Math.max(64, sh.get());
            }
            ImGui.textDisabled("(shadow size needs restart)");

            // Light space
            ImGui.separator();
            ImGui.text("Light Space");
            sliderFloat("Light Near", () -> EngineConfig.Render.LIGHT_NEAR, v -> EngineConfig.Render.LIGHT_NEAR = v,
                    0.1f, 10f);
            sliderFloat("Light Far", () -> EngineConfig.Render.LIGHT_FAR, v -> EngineConfig.Render.LIGHT_FAR = v, 10f,
                    200f);
            sliderFloat("Light Ortho Half", () -> EngineConfig.Render.LIGHT_ORTHO_HALF,
                    v -> EngineConfig.Render.LIGHT_ORTHO_HALF = v, 1f, 60f);

            // Camera projection
            ImGui.separator();
            ImGui.text("Camera Projection");
            sliderFloat("FOV (deg)", () -> EngineConfig.Render.CAM_FOV_DEG, v -> EngineConfig.Render.CAM_FOV_DEG = v,
                    20f, 120f);
            sliderFloat("Near", () -> EngineConfig.Render.CAM_NEAR, v -> EngineConfig.Render.CAM_NEAR = v, 0.01f, 1f);
            sliderFloat("Far", () -> EngineConfig.Render.CAM_FAR, v -> EngineConfig.Render.CAM_FAR = v, 50f, 500f);
            sliderFloat("Ortho Half Size", () -> EngineConfig.Render.ORTHO_HALF_SIZE,
                    v -> EngineConfig.Render.ORTHO_HALF_SIZE = v, 1f, 30f);

            // Clear color
            ImGui.separator();
            ImGui.text("Clear Color");
            float[] clear = EngineConfig.Render.CLEAR_COLOR;
            float[] clearEdit = { clear[0], clear[1], clear[2], clear.length > 3 ? clear[3] : 1.0f };
            if (ImGui.colorEdit4("Clear Color##picker", clearEdit)) {
                EngineConfig.Render.CLEAR_COLOR = new float[] { clearEdit[0], clearEdit[1], clearEdit[2],
                        clearEdit[3] };
            }

            // Object colors
            ImGui.separator();
            ImGui.text("Object Colors");
            colorEdit3("Ground", EngineConfig.Render.COLOR_GROUND);
            colorEdit3("Grid", EngineConfig.Render.COLOR_GRID);
            colorEdit3("Player Ground", EngineConfig.Render.COLOR_PLAYER_GROUND);
            colorEdit3("Player Air", EngineConfig.Render.COLOR_PLAYER_AIR);
            colorEdit3("Block", EngineConfig.Render.COLOR_BLOCK);
            colorEdit3("Highlight", EngineConfig.Render.COLOR_HIGHLIGHT);

            // Ground
            ImGui.separator();
            ImGui.text("Ground / Grid");
            sliderFloat("Ground Half", () -> EngineConfig.Render.GROUND_HALF, v -> EngineConfig.Render.GROUND_HALF = v,
                    5f, 200f);
            sliderInt("Grid Half Count", () -> EngineConfig.Render.GRID_HALF_COUNT,
                    v -> EngineConfig.Render.GRID_HALF_COUNT = v, 1, 200);

            // Sphere
            ImGui.separator();
            ImGui.text("Sphere Geometry");
            sliderInt("Stacks", () -> EngineConfig.Render.SPHERE_STACKS,
                    v -> EngineConfig.Render.SPHERE_STACKS = Math.max(3, v), 3, 64);
            sliderInt("Slices", () -> EngineConfig.Render.SPHERE_SLICES,
                    v -> EngineConfig.Render.SPHERE_SLICES = Math.max(3, v), 3, 64);
            sliderFloat("Radius", () -> EngineConfig.Render.SPHERE_RADIUS, v -> EngineConfig.Render.SPHERE_RADIUS = v,
                    0.1f, 2f);
        }

        // ============================================================
        // Gizmo
        // ============================================================
        if (ImGui.collapsingHeader("Gizmo")) {
            sliderFloat("Axis Length", () -> EngineConfig.Gizmo.AXIS_LENGTH, v -> EngineConfig.Gizmo.AXIS_LENGTH = v,
                    0.5f, 10f);
            sliderFloat("Axis Thickness", () -> EngineConfig.Gizmo.AXIS_THICKNESS,
                    v -> EngineConfig.Gizmo.AXIS_THICKNESS = v, 0.05f, 1f);
            sliderFloat("Handle Cube Size", () -> EngineConfig.Gizmo.HANDLE_CUBE_SIZE,
                    v -> EngineConfig.Gizmo.HANDLE_CUBE_SIZE = v, 0.05f, 1f);
            sliderFloat("Ring Radius", () -> EngineConfig.Gizmo.RING_RADIUS, v -> EngineConfig.Gizmo.RING_RADIUS = v,
                    0.5f, 10f);
            sliderFloat("Ring Thickness", () -> EngineConfig.Gizmo.RING_THICKNESS,
                    v -> EngineConfig.Gizmo.RING_THICKNESS = v, 0.05f, 1f);
            sliderInt("Ring Segments", () -> EngineConfig.Gizmo.RING_SEGMENTS,
                    v -> EngineConfig.Gizmo.RING_SEGMENTS = Math.max(6, v), 6, 128);
        }

        // ============================================================
        // Editor
        // ============================================================
        if (ImGui.collapsingHeader("Editor")) {
            ImGui.text("Focus (F key)");
            sliderFloat("Min Distance", () -> EngineConfig.Editor.FOCUS_MIN_DISTANCE,
                    v -> EngineConfig.Editor.FOCUS_MIN_DISTANCE = v, 0.5f, 20f);
            sliderFloat("Size Factor", () -> EngineConfig.Editor.FOCUS_SIZE_FACTOR,
                    v -> EngineConfig.Editor.FOCUS_SIZE_FACTOR = v, 1f, 10f);

            ImGui.separator();
            ImGui.text("Orbit (Alt+RMouse)");
            sliderFloat("Rot Speed", () -> EngineConfig.Editor.ORBIT_ROT_SPEED,
                    v -> EngineConfig.Editor.ORBIT_ROT_SPEED = v, 0.001f, 0.02f);
            sliderFloat("Pitch Limit", () -> EngineConfig.Editor.ORBIT_PITCH_LIMIT,
                    v -> EngineConfig.Editor.ORBIT_PITCH_LIMIT = v, 0.5f, 3.14f);
            sliderFloat("Zoom Factor", () -> EngineConfig.Editor.ORBIT_ZOOM_FACTOR,
                    v -> EngineConfig.Editor.ORBIT_ZOOM_FACTOR = v, 0.01f, 1f);
            sliderFloat("Dist Min", () -> EngineConfig.Editor.ORBIT_DIST_MIN,
                    v -> EngineConfig.Editor.ORBIT_DIST_MIN = v, 0.1f, 5f);
            sliderFloat("Dist Max", () -> EngineConfig.Editor.ORBIT_DIST_MAX,
                    v -> EngineConfig.Editor.ORBIT_DIST_MAX = v, 5f, 200f);

            ImGui.separator();
            ImGui.text("Drag");
            sliderFloat("Drag Sensitivity", () -> EngineConfig.Editor.DRAG_SENSITIVITY,
                    v -> EngineConfig.Editor.DRAG_SENSITIVITY = v, 0.001f, 0.1f);
            sliderFloat("Rot Drag Speed", () -> EngineConfig.Editor.ROT_DRAG_SPEED,
                    v -> EngineConfig.Editor.ROT_DRAG_SPEED = v, 0.001f, 0.1f);
            sliderFloat("Scale Min", () -> EngineConfig.Editor.SCALE_MIN, v -> EngineConfig.Editor.SCALE_MIN = v, 0.01f,
                    1f);
        }

        // ============================================================
        // Camera
        // ============================================================
        if (ImGui.collapsingHeader("Camera")) {
            sliderFloat("Distance", () -> EngineConfig.Camera.CAM_DISTANCE, v -> EngineConfig.Camera.CAM_DISTANCE = v,
                    1f, 30f);
            sliderFloat("Editor Speed", () -> EngineConfig.Camera.EDITOR_SPEED,
                    v -> EngineConfig.Camera.EDITOR_SPEED = v, 0.01f, 2f);
            sliderFloat("Editor Speed Mult", () -> EngineConfig.Camera.EDITOR_SPEED_MULTIPLIER,
                    v -> EngineConfig.Camera.EDITOR_SPEED_MULTIPLIER = v, 1f, 10f);
            sliderFloat("Play Look Sens", () -> EngineConfig.Camera.PLAY_LOOK_SENSITIVITY,
                    v -> EngineConfig.Camera.PLAY_LOOK_SENSITIVITY = v, 0.0001f, 0.02f);
            sliderFloat("Editor Look Sens", () -> EngineConfig.Camera.EDITOR_LOOK_SENSITIVITY,
                    v -> EngineConfig.Camera.EDITOR_LOOK_SENSITIVITY = v, 0.0001f, 0.02f);
            sliderFloat("Smooth Lerp", () -> EngineConfig.Camera.SMOOTH_LERP, v -> EngineConfig.Camera.SMOOTH_LERP = v,
                    0.01f, 1f);
            sliderFloat("Ortho Lerp", () -> EngineConfig.Camera.ORTHO_LERP, v -> EngineConfig.Camera.ORTHO_LERP = v,
                    0.01f, 1f);
            sliderFloat("Target Y Offset", () -> EngineConfig.Camera.TARGET_Y_OFFSET,
                    v -> EngineConfig.Camera.TARGET_Y_OFFSET = v, 0f, 2f);
            sliderFloat("Editor Zoom Speed", () -> EngineConfig.Camera.EDITOR_ZOOM_SPEED,
                    v -> EngineConfig.Camera.EDITOR_ZOOM_SPEED = v, 0.1f, 3f);

            ImGui.separator();
            sliderFloat("Play Pitch Min (deg)", () -> EngineConfig.Camera.PLAY_PITCH_MIN_DEG,
                    v -> EngineConfig.Camera.PLAY_PITCH_MIN_DEG = v, -90f, 0f);
            sliderFloat("Play Pitch Max (deg)", () -> EngineConfig.Camera.PLAY_PITCH_MAX_DEG,
                    v -> EngineConfig.Camera.PLAY_PITCH_MAX_DEG = v, 0f, 90f);
            sliderFloat("Edit Pitch Limit (deg)", () -> EngineConfig.Camera.EDIT_PITCH_LIMIT_DEG,
                    v -> EngineConfig.Camera.EDIT_PITCH_LIMIT_DEG = v, 10f, 90f);
        }

        // ============================================================
        // Game
        // ============================================================
        if (ImGui.collapsingHeader("Game")) {
            sliderFloat("Move Speed", () -> EngineConfig.Game.MOVE_SPEED, v -> EngineConfig.Game.MOVE_SPEED = v, 0.01f,
                    1f);
            sliderFloat("Scale Lerp", () -> EngineConfig.Game.SCALE_LERP, v -> EngineConfig.Game.SCALE_LERP = v, 0.01f,
                    1f);

            ImGui.separator();
            ImGui.text("Jump / Land Squash");
            sliderFloat("Jump Scale Y", () -> EngineConfig.Game.JUMP_SCALE_Y, v -> EngineConfig.Game.JUMP_SCALE_Y = v,
                    0.5f, 2f);
            sliderFloat("Jump Scale XZ", () -> EngineConfig.Game.JUMP_SCALE_XZ,
                    v -> EngineConfig.Game.JUMP_SCALE_XZ = v, 0.5f, 2f);
            sliderFloat("Land Scale Y", () -> EngineConfig.Game.LAND_SCALE_Y, v -> EngineConfig.Game.LAND_SCALE_Y = v,
                    0.5f, 2f);
            sliderFloat("Land Scale XZ", () -> EngineConfig.Game.LAND_SCALE_XZ,
                    v -> EngineConfig.Game.LAND_SCALE_XZ = v, 0.5f, 2f);
        }

        // ============================================================
        // Physics
        // ============================================================
        if (ImGui.collapsingHeader("Physics")) {
            sliderFloat("Gravity", () -> EngineConfig.Physics.GRAVITY, v -> EngineConfig.Physics.GRAVITY = v, -0.1f,
                    0f);
            sliderFloat("Jump Strength", () -> EngineConfig.Physics.JUMP_STRENGTH,
                    v -> EngineConfig.Physics.JUMP_STRENGTH = v, 0.05f, 1f);
        }

        // ============================================================
        // Light
        // ============================================================
        if (ImGui.collapsingHeader("Light")) {
            float[] pos = { EngineConfig.Light.POS_X, EngineConfig.Light.POS_Y, EngineConfig.Light.POS_Z };
            if (ImGui.sliderFloat3("Position", pos, -30f, 30f)) {
                EngineConfig.Light.POS_X = pos[0];
                EngineConfig.Light.POS_Y = pos[1];
                EngineConfig.Light.POS_Z = pos[2];
            }
            sliderFloat("Ambient", () -> EngineConfig.Light.AMBIENT, v -> EngineConfig.Light.AMBIENT = v, 0f, 2f);
            sliderFloat("Diffuse", () -> EngineConfig.Light.DIFFUSE, v -> EngineConfig.Light.DIFFUSE = v, 0f, 2f);
        }

        ImGui.end();
    }

    // ============================================================
    // 헬퍼: 슬라이더 (getter / setter 람다)
    // ============================================================
    private interface FloatGetter {
        float get();
    }

    private interface FloatSetter {
        void set(float v);
    }

    private interface IntGetter {
        int get();
    }

    private interface IntSetter {
        void set(int v);
    }

    private static void sliderFloat(String label, FloatGetter get, FloatSetter set, float min, float max) {
        float[] v = { get.get() };
        if (ImGui.sliderFloat(label, v, min, max)) {
            set.set(v[0]);
        }
    }

    private static void sliderInt(String label, IntGetter get, IntSetter set, int min, int max) {
        int[] v = { get.get() };
        if (ImGui.sliderInt(label, v, min, max)) {
            set.set(v[0]);
        }
    }

    private static void colorEdit3(String label, float[] color) {
        if (color.length < 3)
            return;
        float[] c = { color[0], color[1], color[2] };
        if (ImGui.colorEdit3(label, c)) {
            color[0] = c[0];
            color[1] = c[1];
            color[2] = c[2];
        }
    }

    // ============================================================
    // 파일 저장 / 리셋 / 리로드
    // ============================================================
    private void saveToFile() {
        Properties p = new Properties();

        // Window
        p.setProperty("window.width", String.valueOf(EngineConfig.Window.WIDTH));
        p.setProperty("window.height", String.valueOf(EngineConfig.Window.HEIGHT));
        p.setProperty("window.title", EngineConfig.Window.TITLE);
        p.setProperty("window.swapInterval", String.valueOf(EngineConfig.Window.SWAP_INTERVAL));

        // Engine
        p.setProperty("engine.dtClamp", String.valueOf(EngineConfig.Engine.DT_CLAMP));
        p.setProperty("engine.glslVersion", EngineConfig.Engine.GLSL_VERSION);

        // Render
        p.setProperty("render.shadowWidth", String.valueOf(EngineConfig.Render.SHADOW_WIDTH));
        p.setProperty("render.shadowHeight", String.valueOf(EngineConfig.Render.SHADOW_HEIGHT));
        p.setProperty("render.lightNear", String.valueOf(EngineConfig.Render.LIGHT_NEAR));
        p.setProperty("render.lightFar", String.valueOf(EngineConfig.Render.LIGHT_FAR));
        p.setProperty("render.lightOrthoHalf", String.valueOf(EngineConfig.Render.LIGHT_ORTHO_HALF));
        p.setProperty("render.camFovDeg", String.valueOf(EngineConfig.Render.CAM_FOV_DEG));
        p.setProperty("render.camNear", String.valueOf(EngineConfig.Render.CAM_NEAR));
        p.setProperty("render.camFar", String.valueOf(EngineConfig.Render.CAM_FAR));
        p.setProperty("render.orthoHalfSize", String.valueOf(EngineConfig.Render.ORTHO_HALF_SIZE));
        p.setProperty("render.clearColor", join(EngineConfig.Render.CLEAR_COLOR));
        p.setProperty("render.groundHalf", String.valueOf(EngineConfig.Render.GROUND_HALF));
        p.setProperty("render.gridHalfCount", String.valueOf(EngineConfig.Render.GRID_HALF_COUNT));
        p.setProperty("render.gridLineY", String.valueOf(EngineConfig.Render.GRID_LINE_Y));
        p.setProperty("render.sphereStacks", String.valueOf(EngineConfig.Render.SPHERE_STACKS));
        p.setProperty("render.sphereSlices", String.valueOf(EngineConfig.Render.SPHERE_SLICES));
        p.setProperty("render.sphereRadius", String.valueOf(EngineConfig.Render.SPHERE_RADIUS));
        p.setProperty("render.cubeHalf", String.valueOf(EngineConfig.Render.CUBE_HALF));
        p.setProperty("render.colorGround", join(EngineConfig.Render.COLOR_GROUND));
        p.setProperty("render.colorGrid", join(EngineConfig.Render.COLOR_GRID));
        p.setProperty("render.colorPlayerGround", join(EngineConfig.Render.COLOR_PLAYER_GROUND));
        p.setProperty("render.colorPlayerAir", join(EngineConfig.Render.COLOR_PLAYER_AIR));
        p.setProperty("render.colorBlock", join(EngineConfig.Render.COLOR_BLOCK));
        p.setProperty("render.colorHighlight", join(EngineConfig.Render.COLOR_HIGHLIGHT));
        p.setProperty("render.highlightScale", String.valueOf(EngineConfig.Render.HIGHLIGHT_SCALE));
        p.setProperty("render.lineWidthDefault", String.valueOf(EngineConfig.Render.LINE_WIDTH_DEFAULT));
        p.setProperty("render.lineWidthHighlight", String.valueOf(EngineConfig.Render.LINE_WIDTH_HIGHLIGHT));
        p.setProperty("render.lineWidthPosition", String.valueOf(EngineConfig.Render.LINE_WIDTH_POSITION));
        p.setProperty("render.lineWidthScaleRot", String.valueOf(EngineConfig.Render.LINE_WIDTH_SCALE_ROT));

        // Gizmo
        p.setProperty("gizmo.axisLength", String.valueOf(EngineConfig.Gizmo.AXIS_LENGTH));
        p.setProperty("gizmo.axisThickness", String.valueOf(EngineConfig.Gizmo.AXIS_THICKNESS));
        p.setProperty("gizmo.handleCubeSize", String.valueOf(EngineConfig.Gizmo.HANDLE_CUBE_SIZE));
        p.setProperty("gizmo.ringRadius", String.valueOf(EngineConfig.Gizmo.RING_RADIUS));
        p.setProperty("gizmo.ringThickness", String.valueOf(EngineConfig.Gizmo.RING_THICKNESS));
        p.setProperty("gizmo.ringSegments", String.valueOf(EngineConfig.Gizmo.RING_SEGMENTS));
        p.setProperty("gizmo.pickEpsilon", String.valueOf(EngineConfig.Gizmo.PICK_EPSILON));

        // Editor
        p.setProperty("editor.focusMinDistance", String.valueOf(EngineConfig.Editor.FOCUS_MIN_DISTANCE));
        p.setProperty("editor.focusSizeFactor", String.valueOf(EngineConfig.Editor.FOCUS_SIZE_FACTOR));
        p.setProperty("editor.orbitRotSpeed", String.valueOf(EngineConfig.Editor.ORBIT_ROT_SPEED));
        p.setProperty("editor.orbitPitchLimit", String.valueOf(EngineConfig.Editor.ORBIT_PITCH_LIMIT));
        p.setProperty("editor.orbitZoomFactor", String.valueOf(EngineConfig.Editor.ORBIT_ZOOM_FACTOR));
        p.setProperty("editor.orbitDistMin", String.valueOf(EngineConfig.Editor.ORBIT_DIST_MIN));
        p.setProperty("editor.orbitDistMax", String.valueOf(EngineConfig.Editor.ORBIT_DIST_MAX));
        p.setProperty("editor.orbitDistFallback", String.valueOf(EngineConfig.Editor.ORBIT_DIST_FALLBACK));
        p.setProperty("editor.dragSensitivity", String.valueOf(EngineConfig.Editor.DRAG_SENSITIVITY));
        p.setProperty("editor.rotDragSpeed", String.valueOf(EngineConfig.Editor.ROT_DRAG_SPEED));
        p.setProperty("editor.scaleMin", String.valueOf(EngineConfig.Editor.SCALE_MIN));
        p.setProperty("editor.initOffsetX", String.valueOf(EngineConfig.Editor.INIT_OFFSET_X));
        p.setProperty("editor.initOffsetY", String.valueOf(EngineConfig.Editor.INIT_OFFSET_Y));
        p.setProperty("editor.initOffsetZ", String.valueOf(EngineConfig.Editor.INIT_OFFSET_Z));
        p.setProperty("editor.initYaw", String.valueOf(EngineConfig.Editor.INIT_YAW));
        p.setProperty("editor.initPitchDeg", String.valueOf(EngineConfig.Editor.INIT_PITCH_DEG));

        // Camera
        p.setProperty("camera.smoothPosX", String.valueOf(EngineConfig.Camera.SMOOTH_POS_X));
        p.setProperty("camera.smoothPosY", String.valueOf(EngineConfig.Camera.SMOOTH_POS_Y));
        p.setProperty("camera.smoothPosZ", String.valueOf(EngineConfig.Camera.SMOOTH_POS_Z));
        p.setProperty("camera.yaw", String.valueOf(EngineConfig.Camera.CAM_YAW));
        p.setProperty("camera.pitch", String.valueOf(EngineConfig.Camera.CAM_PITCH));
        p.setProperty("camera.distance", String.valueOf(EngineConfig.Camera.CAM_DISTANCE));
        p.setProperty("camera.editorSpeed", String.valueOf(EngineConfig.Camera.EDITOR_SPEED));
        p.setProperty("camera.editorSpeedMultiplier", String.valueOf(EngineConfig.Camera.EDITOR_SPEED_MULTIPLIER));
        p.setProperty("camera.playPitchMinDeg", String.valueOf(EngineConfig.Camera.PLAY_PITCH_MIN_DEG));
        p.setProperty("camera.playPitchMaxDeg", String.valueOf(EngineConfig.Camera.PLAY_PITCH_MAX_DEG));
        p.setProperty("camera.editPitchLimitDeg", String.valueOf(EngineConfig.Camera.EDIT_PITCH_LIMIT_DEG));
        p.setProperty("camera.playLookSensitivity", String.valueOf(EngineConfig.Camera.PLAY_LOOK_SENSITIVITY));
        p.setProperty("camera.editorLookSensitivity", String.valueOf(EngineConfig.Camera.EDITOR_LOOK_SENSITIVITY));
        p.setProperty("camera.smoothLerp", String.valueOf(EngineConfig.Camera.SMOOTH_LERP));
        p.setProperty("camera.orthoLerp", String.valueOf(EngineConfig.Camera.ORTHO_LERP));
        p.setProperty("camera.targetYOffset", String.valueOf(EngineConfig.Camera.TARGET_Y_OFFSET));
        p.setProperty("camera.editorZoomSpeed", String.valueOf(EngineConfig.Camera.EDITOR_ZOOM_SPEED));

        // Game
        p.setProperty("game.playerPosX", String.valueOf(EngineConfig.Game.PLAYER_POS_X));
        p.setProperty("game.playerPosY", String.valueOf(EngineConfig.Game.PLAYER_POS_Y));
        p.setProperty("game.playerPosZ", String.valueOf(EngineConfig.Game.PLAYER_POS_Z));
        p.setProperty("game.playerSize", String.valueOf(EngineConfig.Game.PLAYER_SIZE));
        p.setProperty("game.moveSpeed", String.valueOf(EngineConfig.Game.MOVE_SPEED));
        p.setProperty("game.scaleLerp", String.valueOf(EngineConfig.Game.SCALE_LERP));
        p.setProperty("game.jumpScaleY", String.valueOf(EngineConfig.Game.JUMP_SCALE_Y));
        p.setProperty("game.jumpScaleXZ", String.valueOf(EngineConfig.Game.JUMP_SCALE_XZ));
        p.setProperty("game.landScaleY", String.valueOf(EngineConfig.Game.LAND_SCALE_Y));
        p.setProperty("game.landScaleXZ", String.valueOf(EngineConfig.Game.LAND_SCALE_XZ));
        p.setProperty("game.initialBlocks", joinBlocks(EngineConfig.Game.INITIAL_BLOCKS));

        // Physics
        p.setProperty("physics.gravity", String.valueOf(EngineConfig.Physics.GRAVITY));
        p.setProperty("physics.jumpStrength", String.valueOf(EngineConfig.Physics.JUMP_STRENGTH));
        p.setProperty("physics.velocityY", String.valueOf(EngineConfig.Physics.VELOCITY_Y));
        p.setProperty("physics.scaleInit", String.valueOf(EngineConfig.Physics.SCALE_INIT));

        // Light
        p.setProperty("light.posX", String.valueOf(EngineConfig.Light.POS_X));
        p.setProperty("light.posY", String.valueOf(EngineConfig.Light.POS_Y));
        p.setProperty("light.posZ", String.valueOf(EngineConfig.Light.POS_Z));
        p.setProperty("light.ambient", String.valueOf(EngineConfig.Light.AMBIENT));
        p.setProperty("light.diffuse", String.valueOf(EngineConfig.Light.DIFFUSE));

        // 파일 저장 (주석 헤더 추가)
        Path path = Paths.get(savePath.get());
        try (OutputStream out = Files.newOutputStream(path)) {
            p.store(out, "Engine Configuration (saved from ImGui)");
            lastSaveMessage = "Saved!";
            lastSaveMessageTime = ImGui.getTime();
            System.out.println("[EngineConfigUi] saved to " + path.toAbsolutePath());
        } catch (IOException e) {
            lastSaveMessage = "Save failed: " + e.getMessage();
            lastSaveMessageTime = ImGui.getTime();
            System.err.println("[EngineConfigUi] save failed: " + e.getMessage());
        }
    }

    private void resetToDefaults() {
        // load()의 fallback 기본값으로 복원하려면 파일을 무시하고 다시 적용해야 함.
        // 여기서는 "코드에 하드코딩된 초기값"으로 되돌리는 대신,
        // Properties를 비우고 재적용하는 방식을 쓴다.
        // (EngineConfig.load()가 멱등이라 재호출해도 no-op이므로 별도 메서드 필요)
        EngineConfig.resetToDefaults();
        lastSaveMessage = "Reset!";
        lastSaveMessageTime = ImGui.getTime();
    }

    private void reloadFromFile() {
        EngineConfig.reload();
        lastSaveMessage = "Reloaded!";
        lastSaveMessageTime = ImGui.getTime();
    }

    private static String join(float[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    private static String joinBlocks(float[][] blocks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < blocks.length; i++) {
            if (i > 0)
                sb.append(" ; ");
            float[] b = blocks[i];
            for (int j = 0; j < b.length; j++) {
                if (j > 0)
                    sb.append(",");
                sb.append(b[j]);
            }
        }
        return sb.toString();
    }
}