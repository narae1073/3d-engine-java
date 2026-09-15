import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 엔진 전역 설정.
 *
 * <p>
 * 우선순위(높은 순):
 * <ol>
 * <li>JVM 시스템 프로퍼티 (예: {@code -Dgame.moveSpeed=0.2})</li>
 * <li>외부 파일 {@code ./engine.properties} (실행 디렉터리)</li>
 * <li>클래스패스 리소스 {@code /engine.properties}</li>
 * <li>코드 내 fallback 기본값</li>
 * </ol>
 *
 * <p>
 * 모든 필드는 {@code public static}이며, 로드 후에도 런타임에 수정 가능하다.
 * (ImGui 설정 패널 등에서 활용)
 */
public final class EngineConfig {
    private EngineConfig() {
    }

    // ============================================================
    // 로더
    // ============================================================
    private static Properties props = new Properties();
    private static boolean loaded = false;

    // 초기 코드 기본값 스냅샷 (reset용)
    private static boolean snapshotTaken = false;
    private static Snapshot snapshot;

    /** 프로그램 시작 시 1회 호출. 여러 번 호출해도 안전(멱등). */
    public static synchronized void load() {
        if (loaded)
            return;
        loaded = true;
        takeSnapshot(); // 최초 코드값 저장
        doLoad(); // 파일 적용

        // 1) 클래스패스 리소스
        try (InputStream in = EngineConfig.class.getResourceAsStream("/engine.properties")) {
            if (in != null) {
                props.load(in);
                System.out.println("[EngineConfig] loaded classpath:/engine.properties");
            }
        } catch (IOException e) {
            System.err.println("[EngineConfig] classpath load failed: " + e.getMessage());
        }

        // 2) 외부 파일 (있으면 덮어씀)
        Path external = Paths.get("engine.properties");
        if (Files.exists(external)) {
            try (InputStream in = Files.newInputStream(external)) {
                props.load(in);
                System.out.println("[EngineConfig] overrode with ./engine.properties");
            } catch (IOException e) {
                System.err.println("[EngineConfig] external load failed: " + e.getMessage());
            }
        }

        // 3) 필드에 반영
        Window.apply();
        Engine.apply();
        Render.apply();
        Gizmo.apply();
        Editor.apply();
        Camera.apply();
        Game.apply();
        Physics.apply();
        Light.apply();
    }

    /** 파일을 다시 읽어 필드에 반영. (재시작 없이) */
    public static synchronized void reload() {
        if (!snapshotTaken)
            takeSnapshot();
        props = new Properties();
        doLoad();
    }

    /** 코드에 하드코딩된 초기값으로 복원. */
    public static synchronized void resetToDefaults() {
        if (!snapshotTaken)
            return;
        snapshot.restore();
    }

    private static void doLoad() {
        // 1) 클래스패스
        try (InputStream in = EngineConfig.class.getResourceAsStream("/engine.properties")) {
            if (in != null)
                props.load(in);
        } catch (IOException e) {
            System.err.println("[EngineConfig] classpath load failed: " + e.getMessage());
        }
        // 2) 외부 파일
        Path external = Paths.get("engine.properties");
        if (Files.exists(external)) {
            try (InputStream in = Files.newInputStream(external)) {
                props.load(in);
            } catch (IOException e) {
                System.err.println("[EngineConfig] external load failed: " + e.getMessage());
            }
        }
        // 3) 필드 반영
        Window.apply();
        Engine.apply();
        Render.apply();
        Gizmo.apply();
        Editor.apply();
        Camera.apply();
        Game.apply();
        Physics.apply();
        Light.apply();
    }

    // --- 스냅샷 ---
    private static void takeSnapshot() {
        snapshot = new Snapshot();
        snapshotTaken = true;
    }

    // ============================================================
    // Snapshot — 9개 그룹 전체 필드 저장/복원
    // ============================================================
    /**
     * 코드 하드코딩 기본값의 스냅샷.
     *
     * <p>
     * 모든 필드는 {@code final}이며 생성 시점의 값을 복사해 둔다.
     * {@link #restore()}는 스냅샷 값을 {@link EngineConfig}의 각 그룹 필드에 되돌려 놓는다.
     *
     * <p>
     * 배열 필드는 반드시 {@code clone()}으로 깊은 복사하여 원본과의
     * 참조 공유를 방지한다. (복원 시 다시 {@code clone()} 하여 스냅샷 자체가
     * 이후 변경에 오염되지 않도록 함)
     */
    private static final class Snapshot {

        // ---------- Window ----------
        final int winWidth;
        final int winHeight;
        final String winTitle;
        final int winSwapInterval;

        // ---------- Engine ----------
        final float engineDtClamp;
        final String engineGlslVersion;

        // ---------- Render ----------
        final int shadowWidth;
        final int shadowHeight;
        final float lightNear;
        final float lightFar;
        final float lightOrthoHalf;
        final float camFovDeg;
        final float camNear;
        final float camFar;
        final float orthoHalfSize;
        final float[] clearColor;
        final float groundHalf;
        final int gridHalfCount;
        final float gridLineY;
        final int sphereStacks;
        final int sphereSlices;
        final float sphereRadius;
        final float cubeHalf;
        final float[] colorGround;
        final float[] colorGrid;
        final float[] colorPlayerGround;
        final float[] colorPlayerAir;
        final float[] colorBlock;
        final float[] colorHighlight;
        final float highlightScale;
        final float lineWidthDefault;
        final float lineWidthHighlight;
        final float lineWidthPosition;
        final float lineWidthScaleRot;

        // ---------- Gizmo ----------
        final float gizmoAxisLength;
        final float gizmoAxisThickness;
        final float gizmoHandleCubeSize;
        final float gizmoRingRadius;
        final float gizmoRingThickness;
        final int gizmoRingSegments;
        final float gizmoPickEpsilon;

        // ---------- Editor ----------
        final float editorFocusMinDistance;
        final float editorFocusSizeFactor;
        final float editorOrbitRotSpeed;
        final float editorOrbitPitchLimit;
        final float editorOrbitZoomFactor;
        final float editorOrbitDistMin;
        final float editorOrbitDistMax;
        final float editorOrbitDistFallback;
        final float editorDragSensitivity;
        final float editorRotDragSpeed;
        final float editorScaleMin;
        final float editorInitOffsetX;
        final float editorInitOffsetY;
        final float editorInitOffsetZ;
        final float editorInitYaw;
        final float editorInitPitchDeg;

        // ---------- Camera ----------
        final float camSmoothPosX;
        final float camSmoothPosY;
        final float camSmoothPosZ;
        final float camYaw;
        final float camPitch;
        final float camDistance;
        final float camEditorSpeed;
        final float camEditorSpeedMultiplier;
        final float camPlayPitchMinDeg;
        final float camPlayPitchMaxDeg;
        final float camEditPitchLimitDeg;
        final float camPlayLookSensitivity;
        final float camEditorLookSensitivity;
        final float camSmoothLerp;
        final float camOrthoLerp;
        final float camTargetYOffset;
        final float camEditorZoomSpeed;

        // ---------- Game ----------
        final float gamePlayerPosX;
        final float gamePlayerPosY;
        final float gamePlayerPosZ;
        final float gamePlayerSize;
        final float gameMoveSpeed;
        final float gameScaleLerp;
        final float gameJumpScaleY;
        final float gameJumpScaleXZ;
        final float gameLandScaleY;
        final float gameLandScaleXZ;
        final float[][] gameInitialBlocks;

        // ---------- Physics ----------
        final float physicsGravity;
        final float physicsJumpStrength;
        final float physicsVelocityY;
        final float physicsScaleInit;

        // ---------- Light ----------
        final float lightPosX;
        final float lightPosY;
        final float lightPosZ;
        final float lightAmbient;
        final float lightDiffuse;

        // ============================================================
        // 생성자 — 현재 필드값 캡처
        // ============================================================
        Snapshot() {
            // Window
            winWidth = Window.WIDTH;
            winHeight = Window.HEIGHT;
            winTitle = Window.TITLE;
            winSwapInterval = Window.SWAP_INTERVAL;

            // Engine
            engineDtClamp = Engine.DT_CLAMP;
            engineGlslVersion = Engine.GLSL_VERSION;

            // Render
            shadowWidth = Render.SHADOW_WIDTH;
            shadowHeight = Render.SHADOW_HEIGHT;
            lightNear = Render.LIGHT_NEAR;
            lightFar = Render.LIGHT_FAR;
            lightOrthoHalf = Render.LIGHT_ORTHO_HALF;
            camFovDeg = Render.CAM_FOV_DEG;
            camNear = Render.CAM_NEAR;
            camFar = Render.CAM_FAR;
            orthoHalfSize = Render.ORTHO_HALF_SIZE;
            clearColor = Render.CLEAR_COLOR.clone();
            groundHalf = Render.GROUND_HALF;
            gridHalfCount = Render.GRID_HALF_COUNT;
            gridLineY = Render.GRID_LINE_Y;
            sphereStacks = Render.SPHERE_STACKS;
            sphereSlices = Render.SPHERE_SLICES;
            sphereRadius = Render.SPHERE_RADIUS;
            cubeHalf = Render.CUBE_HALF;
            colorGround = Render.COLOR_GROUND.clone();
            colorGrid = Render.COLOR_GRID.clone();
            colorPlayerGround = Render.COLOR_PLAYER_GROUND.clone();
            colorPlayerAir = Render.COLOR_PLAYER_AIR.clone();
            colorBlock = Render.COLOR_BLOCK.clone();
            colorHighlight = Render.COLOR_HIGHLIGHT.clone();
            highlightScale = Render.HIGHLIGHT_SCALE;
            lineWidthDefault = Render.LINE_WIDTH_DEFAULT;
            lineWidthHighlight = Render.LINE_WIDTH_HIGHLIGHT;
            lineWidthPosition = Render.LINE_WIDTH_POSITION;
            lineWidthScaleRot = Render.LINE_WIDTH_SCALE_ROT;

            // Gizmo
            gizmoAxisLength = Gizmo.AXIS_LENGTH;
            gizmoAxisThickness = Gizmo.AXIS_THICKNESS;
            gizmoHandleCubeSize = Gizmo.HANDLE_CUBE_SIZE;
            gizmoRingRadius = Gizmo.RING_RADIUS;
            gizmoRingThickness = Gizmo.RING_THICKNESS;
            gizmoRingSegments = Gizmo.RING_SEGMENTS;
            gizmoPickEpsilon = Gizmo.PICK_EPSILON;

            // Editor
            editorFocusMinDistance = Editor.FOCUS_MIN_DISTANCE;
            editorFocusSizeFactor = Editor.FOCUS_SIZE_FACTOR;
            editorOrbitRotSpeed = Editor.ORBIT_ROT_SPEED;
            editorOrbitPitchLimit = Editor.ORBIT_PITCH_LIMIT;
            editorOrbitZoomFactor = Editor.ORBIT_ZOOM_FACTOR;
            editorOrbitDistMin = Editor.ORBIT_DIST_MIN;
            editorOrbitDistMax = Editor.ORBIT_DIST_MAX;
            editorOrbitDistFallback = Editor.ORBIT_DIST_FALLBACK;
            editorDragSensitivity = Editor.DRAG_SENSITIVITY;
            editorRotDragSpeed = Editor.ROT_DRAG_SPEED;
            editorScaleMin = Editor.SCALE_MIN;
            editorInitOffsetX = Editor.INIT_OFFSET_X;
            editorInitOffsetY = Editor.INIT_OFFSET_Y;
            editorInitOffsetZ = Editor.INIT_OFFSET_Z;
            editorInitYaw = Editor.INIT_YAW;
            editorInitPitchDeg = Editor.INIT_PITCH_DEG;

            // Camera
            camSmoothPosX = Camera.SMOOTH_POS_X;
            camSmoothPosY = Camera.SMOOTH_POS_Y;
            camSmoothPosZ = Camera.SMOOTH_POS_Z;
            camYaw = Camera.CAM_YAW;
            camPitch = Camera.CAM_PITCH;
            camDistance = Camera.CAM_DISTANCE;
            camEditorSpeed = Camera.EDITOR_SPEED;
            camEditorSpeedMultiplier = Camera.EDITOR_SPEED_MULTIPLIER;
            camPlayPitchMinDeg = Camera.PLAY_PITCH_MIN_DEG;
            camPlayPitchMaxDeg = Camera.PLAY_PITCH_MAX_DEG;
            camEditPitchLimitDeg = Camera.EDIT_PITCH_LIMIT_DEG;
            camPlayLookSensitivity = Camera.PLAY_LOOK_SENSITIVITY;
            camEditorLookSensitivity = Camera.EDITOR_LOOK_SENSITIVITY;
            camSmoothLerp = Camera.SMOOTH_LERP;
            camOrthoLerp = Camera.ORTHO_LERP;
            camTargetYOffset = Camera.TARGET_Y_OFFSET;
            camEditorZoomSpeed = Camera.EDITOR_ZOOM_SPEED;

            // Game
            gamePlayerPosX = Game.PLAYER_POS_X;
            gamePlayerPosY = Game.PLAYER_POS_Y;
            gamePlayerPosZ = Game.PLAYER_POS_Z;
            gamePlayerSize = Game.PLAYER_SIZE;
            gameMoveSpeed = Game.MOVE_SPEED;
            gameScaleLerp = Game.SCALE_LERP;
            gameJumpScaleY = Game.JUMP_SCALE_Y;
            gameJumpScaleXZ = Game.JUMP_SCALE_XZ;
            gameLandScaleY = Game.LAND_SCALE_Y;
            gameLandScaleXZ = Game.LAND_SCALE_XZ;
            gameInitialBlocks = clone2D(Game.INITIAL_BLOCKS);

            // Physics
            physicsGravity = Physics.GRAVITY;
            physicsJumpStrength = Physics.JUMP_STRENGTH;
            physicsVelocityY = Physics.VELOCITY_Y;
            physicsScaleInit = Physics.SCALE_INIT;

            // Light
            lightPosX = Light.POS_X;
            lightPosY = Light.POS_Y;
            lightPosZ = Light.POS_Z;
            lightAmbient = Light.AMBIENT;
            lightDiffuse = Light.DIFFUSE;
        }

        // ============================================================
        // restore — 스냅샷 값을 필드에 되돌림
        // ============================================================
        void restore() {
            // Window
            Window.WIDTH = winWidth;
            Window.HEIGHT = winHeight;
            Window.TITLE = winTitle;
            Window.SWAP_INTERVAL = winSwapInterval;

            // Engine
            Engine.DT_CLAMP = engineDtClamp;
            Engine.GLSL_VERSION = engineGlslVersion;

            // Render
            Render.SHADOW_WIDTH = shadowWidth;
            Render.SHADOW_HEIGHT = shadowHeight;
            Render.LIGHT_NEAR = lightNear;
            Render.LIGHT_FAR = lightFar;
            Render.LIGHT_ORTHO_HALF = lightOrthoHalf;
            Render.CAM_FOV_DEG = camFovDeg;
            Render.CAM_NEAR = camNear;
            Render.CAM_FAR = camFar;
            Render.ORTHO_HALF_SIZE = orthoHalfSize;
            Render.CLEAR_COLOR = clearColor.clone(); // ★ 다시 clone
            Render.GROUND_HALF = groundHalf;
            Render.GRID_HALF_COUNT = gridHalfCount;
            Render.GRID_LINE_Y = gridLineY;
            Render.SPHERE_STACKS = sphereStacks;
            Render.SPHERE_SLICES = sphereSlices;
            Render.SPHERE_RADIUS = sphereRadius;
            Render.CUBE_HALF = cubeHalf;
            Render.COLOR_GROUND = colorGround.clone(); // ★
            Render.COLOR_GRID = colorGrid.clone();
            Render.COLOR_PLAYER_GROUND = colorPlayerGround.clone();
            Render.COLOR_PLAYER_AIR = colorPlayerAir.clone();
            Render.COLOR_BLOCK = colorBlock.clone();
            Render.COLOR_HIGHLIGHT = colorHighlight.clone();
            Render.HIGHLIGHT_SCALE = highlightScale;
            Render.LINE_WIDTH_DEFAULT = lineWidthDefault;
            Render.LINE_WIDTH_HIGHLIGHT = lineWidthHighlight;
            Render.LINE_WIDTH_POSITION = lineWidthPosition;
            Render.LINE_WIDTH_SCALE_ROT = lineWidthScaleRot;

            // Gizmo
            Gizmo.AXIS_LENGTH = gizmoAxisLength;
            Gizmo.AXIS_THICKNESS = gizmoAxisThickness;
            Gizmo.HANDLE_CUBE_SIZE = gizmoHandleCubeSize;
            Gizmo.RING_RADIUS = gizmoRingRadius;
            Gizmo.RING_THICKNESS = gizmoRingThickness;
            Gizmo.RING_SEGMENTS = gizmoRingSegments;
            Gizmo.PICK_EPSILON = gizmoPickEpsilon;

            // Editor
            Editor.FOCUS_MIN_DISTANCE = editorFocusMinDistance;
            Editor.FOCUS_SIZE_FACTOR = editorFocusSizeFactor;
            Editor.ORBIT_ROT_SPEED = editorOrbitRotSpeed;
            Editor.ORBIT_PITCH_LIMIT = editorOrbitPitchLimit;
            Editor.ORBIT_ZOOM_FACTOR = editorOrbitZoomFactor;
            Editor.ORBIT_DIST_MIN = editorOrbitDistMin;
            Editor.ORBIT_DIST_MAX = editorOrbitDistMax;
            Editor.ORBIT_DIST_FALLBACK = editorOrbitDistFallback;
            Editor.DRAG_SENSITIVITY = editorDragSensitivity;
            Editor.ROT_DRAG_SPEED = editorRotDragSpeed;
            Editor.SCALE_MIN = editorScaleMin;
            Editor.INIT_OFFSET_X = editorInitOffsetX;
            Editor.INIT_OFFSET_Y = editorInitOffsetY;
            Editor.INIT_OFFSET_Z = editorInitOffsetZ;
            Editor.INIT_YAW = editorInitYaw;
            Editor.INIT_PITCH_DEG = editorInitPitchDeg;

            // Camera
            Camera.SMOOTH_POS_X = camSmoothPosX;
            Camera.SMOOTH_POS_Y = camSmoothPosY;
            Camera.SMOOTH_POS_Z = camSmoothPosZ;
            Camera.CAM_YAW = camYaw;
            Camera.CAM_PITCH = camPitch;
            Camera.CAM_DISTANCE = camDistance;
            Camera.EDITOR_SPEED = camEditorSpeed;
            Camera.EDITOR_SPEED_MULTIPLIER = camEditorSpeedMultiplier;
            Camera.PLAY_PITCH_MIN_DEG = camPlayPitchMinDeg;
            Camera.PLAY_PITCH_MAX_DEG = camPlayPitchMaxDeg;
            Camera.EDIT_PITCH_LIMIT_DEG = camEditPitchLimitDeg;
            Camera.PLAY_LOOK_SENSITIVITY = camPlayLookSensitivity;
            Camera.EDITOR_LOOK_SENSITIVITY = camEditorLookSensitivity;
            Camera.SMOOTH_LERP = camSmoothLerp;
            Camera.ORTHO_LERP = camOrthoLerp;
            Camera.TARGET_Y_OFFSET = camTargetYOffset;
            Camera.EDITOR_ZOOM_SPEED = camEditorZoomSpeed;

            // Game
            Game.PLAYER_POS_X = gamePlayerPosX;
            Game.PLAYER_POS_Y = gamePlayerPosY;
            Game.PLAYER_POS_Z = gamePlayerPosZ;
            Game.PLAYER_SIZE = gamePlayerSize;
            Game.MOVE_SPEED = gameMoveSpeed;
            Game.SCALE_LERP = gameScaleLerp;
            Game.JUMP_SCALE_Y = gameJumpScaleY;
            Game.JUMP_SCALE_XZ = gameJumpScaleXZ;
            Game.LAND_SCALE_Y = gameLandScaleY;
            Game.LAND_SCALE_XZ = gameLandScaleXZ;
            Game.INITIAL_BLOCKS = clone2D(gameInitialBlocks); // ★ 깊은 복사

            // Physics
            Physics.GRAVITY = physicsGravity;
            Physics.JUMP_STRENGTH = physicsJumpStrength;
            Physics.VELOCITY_Y = physicsVelocityY;
            Physics.SCALE_INIT = physicsScaleInit;

            // Light
            Light.POS_X = lightPosX;
            Light.POS_Y = lightPosY;
            Light.POS_Z = lightPosZ;
            Light.AMBIENT = lightAmbient;
            Light.DIFFUSE = lightDiffuse;
        }

        // ============================================================
        // 유틸 — 2차원 배열 깊은 복사
        // ============================================================
        private static float[][] clone2D(float[][] src) {
            float[][] dst = new float[src.length][];
            for (int i = 0; i < src.length; i++) {
                dst[i] = src[i].clone();
            }
            return dst;
        }
    }

    // ============================================================
    // 프로퍼티 조회 헬퍼
    // ============================================================
    private static String get(String key, String fallback) {
        // 우선순위: 시스템 프로퍼티 → 파일 → fallback
        String sys = System.getProperty(key);
        if (sys != null)
            return sys;
        return props.getProperty(key, fallback);
    }

    private static int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(get(key, Integer.toString(fallback)).trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("[EngineConfig] invalid int for '" + key + "'", e);
        }
    }

    private static float getFloat(String key, float fallback) {
        try {
            return Float.parseFloat(get(key, Float.toString(fallback)).trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("[EngineConfig] invalid float for '" + key + "'", e);
        }
    }

    private static String getString(String key, String fallback) {
        return get(key, fallback);
    }

    /** "r, g, b" 또는 "r, g, b, a" 형태를 float[]로 파싱. */
    private static float[] getFloatArray(String key, float[] fallback) {
        String raw = get(key, null);
        if (raw == null)
            return fallback.clone();
        String[] parts = raw.split(",");
        float[] out = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Float.parseFloat(parts[i].trim());
        }
        return out;
    }

    // ============================================================
    // Window
    // ============================================================
    public static final class Window {
        private Window() {
        }

        public static int WIDTH = 1920;
        public static int HEIGHT = 1080;
        public static String TITLE = "3D Engine - Shadows & Slime Integrated";
        public static int SWAP_INTERVAL = 1;

        static void apply() {
            WIDTH = getInt("window.width", WIDTH);
            HEIGHT = getInt("window.height", HEIGHT);
            TITLE = getString("window.title", TITLE);
            SWAP_INTERVAL = getInt("window.swapInterval", SWAP_INTERVAL);
        }
    }

    // ============================================================
    // Engine
    // ============================================================
    public static final class Engine {
        private Engine() {
        }

        public static float DT_CLAMP = 0.05f;
        public static String GLSL_VERSION = "#version 120";

        static void apply() {
            DT_CLAMP = getFloat("engine.dtClamp", DT_CLAMP);
            GLSL_VERSION = getString("engine.glslVersion", GLSL_VERSION);
        }
    }

    // ============================================================
    // Render
    // ============================================================
    public static final class Render {
        private Render() {
        }

        public static int SHADOW_WIDTH = 2048;
        public static int SHADOW_HEIGHT = 2048;

        public static float LIGHT_NEAR = 1.0f;
        public static float LIGHT_FAR = 40.0f;
        public static float LIGHT_ORTHO_HALF = 15.0f;

        public static float CAM_FOV_DEG = 60.0f;
        public static float CAM_NEAR = 0.1f;
        public static float CAM_FAR = 100.0f;
        public static float ORTHO_HALF_SIZE = 6.0f;

        public static float[] CLEAR_COLOR = { 0.53f, 0.81f, 0.92f, 1.0f };

        public static float GROUND_HALF = 30.0f;
        public static int GRID_HALF_COUNT = 30;
        public static float GRID_LINE_Y = 0.001f;

        public static int SPHERE_STACKS = 20;
        public static int SPHERE_SLICES = 20;
        public static float SPHERE_RADIUS = 0.5f;
        public static float CUBE_HALF = 0.5f;

        public static float[] COLOR_GROUND = { 0.50f, 0.50f, 0.50f };
        public static float[] COLOR_GRID = { 0.00f, 0.00f, 0.00f };
        public static float[] COLOR_PLAYER_GROUND = { 0.20f, 0.90f, 1.00f };
        public static float[] COLOR_PLAYER_AIR = { 0.10f, 0.60f, 1.00f };
        public static float[] COLOR_BLOCK = { 0.95f, 0.50f, 0.20f };
        public static float[] COLOR_HIGHLIGHT = { 1.00f, 1.00f, 0.00f };

        public static float HIGHLIGHT_SCALE = 1.02f;

        public static float LINE_WIDTH_DEFAULT = 1.0f;
        public static float LINE_WIDTH_HIGHLIGHT = 4.0f;
        public static float LINE_WIDTH_POSITION = 5.0f;
        public static float LINE_WIDTH_SCALE_ROT = 3.0f;

        static void apply() {
            SHADOW_WIDTH = getInt("render.shadowWidth", SHADOW_WIDTH);
            SHADOW_HEIGHT = getInt("render.shadowHeight", SHADOW_HEIGHT);

            LIGHT_NEAR = getFloat("render.lightNear", LIGHT_NEAR);
            LIGHT_FAR = getFloat("render.lightFar", LIGHT_FAR);
            LIGHT_ORTHO_HALF = getFloat("render.lightOrthoHalf", LIGHT_ORTHO_HALF);

            CAM_FOV_DEG = getFloat("render.camFovDeg", CAM_FOV_DEG);
            CAM_NEAR = getFloat("render.camNear", CAM_NEAR);
            CAM_FAR = getFloat("render.camFar", CAM_FAR);
            ORTHO_HALF_SIZE = getFloat("render.orthoHalfSize", ORTHO_HALF_SIZE);

            CLEAR_COLOR = getFloatArray("render.clearColor", CLEAR_COLOR);

            GROUND_HALF = getFloat("render.groundHalf", GROUND_HALF);
            GRID_HALF_COUNT = getInt("render.gridHalfCount", GRID_HALF_COUNT);
            GRID_LINE_Y = getFloat("render.gridLineY", GRID_LINE_Y);

            SPHERE_STACKS = getInt("render.sphereStacks", SPHERE_STACKS);
            SPHERE_SLICES = getInt("render.sphereSlices", SPHERE_SLICES);
            SPHERE_RADIUS = getFloat("render.sphereRadius", SPHERE_RADIUS);
            CUBE_HALF = getFloat("render.cubeHalf", CUBE_HALF);

            COLOR_GROUND = getFloatArray("render.colorGround", COLOR_GROUND);
            COLOR_GRID = getFloatArray("render.colorGrid", COLOR_GRID);
            COLOR_PLAYER_GROUND = getFloatArray("render.colorPlayerGround", COLOR_PLAYER_GROUND);
            COLOR_PLAYER_AIR = getFloatArray("render.colorPlayerAir", COLOR_PLAYER_AIR);
            COLOR_BLOCK = getFloatArray("render.colorBlock", COLOR_BLOCK);
            COLOR_HIGHLIGHT = getFloatArray("render.colorHighlight", COLOR_HIGHLIGHT);

            HIGHLIGHT_SCALE = getFloat("render.highlightScale", HIGHLIGHT_SCALE);

            LINE_WIDTH_DEFAULT = getFloat("render.lineWidthDefault", LINE_WIDTH_DEFAULT);
            LINE_WIDTH_HIGHLIGHT = getFloat("render.lineWidthHighlight", LINE_WIDTH_HIGHLIGHT);
            LINE_WIDTH_POSITION = getFloat("render.lineWidthPosition", LINE_WIDTH_POSITION);
            LINE_WIDTH_SCALE_ROT = getFloat("render.lineWidthScaleRot", LINE_WIDTH_SCALE_ROT);
        }

        /** clearColor를 glClearColor에 바로 넘길 수 있게 float[4]로 반환. */
        public static float[] clearRGBA() {
            float[] c = CLEAR_COLOR;
            if (c.length == 4)
                return c;
            // 3개만 정의된 경우 알파 1.0으로 패딩
            return new float[] { c[0], c[1], c[2], 1.0f };
        }
    }

    // ============================================================
    // Gizmo
    // ============================================================
    public static final class Gizmo {
        private Gizmo() {
        }

        public static float AXIS_LENGTH = 2.0f;
        public static float AXIS_THICKNESS = 0.3f;
        public static float HANDLE_CUBE_SIZE = 0.3f;
        public static float RING_RADIUS = 2.0f;
        public static float RING_THICKNESS = 0.3f;
        public static int RING_SEGMENTS = 32;
        public static float PICK_EPSILON = 1e-6f;

        static void apply() {
            AXIS_LENGTH = getFloat("gizmo.axisLength", AXIS_LENGTH);
            AXIS_THICKNESS = getFloat("gizmo.axisThickness", AXIS_THICKNESS);
            HANDLE_CUBE_SIZE = getFloat("gizmo.handleCubeSize", HANDLE_CUBE_SIZE);
            RING_RADIUS = getFloat("gizmo.ringRadius", RING_RADIUS);
            RING_THICKNESS = getFloat("gizmo.ringThickness", RING_THICKNESS);
            RING_SEGMENTS = getInt("gizmo.ringSegments", RING_SEGMENTS);
            PICK_EPSILON = getFloat("gizmo.pickEpsilon", PICK_EPSILON);
        }
    }

    // ============================================================
    // Editor
    // ============================================================
    public static final class Editor {
        private Editor() {
        }

        public static float FOCUS_MIN_DISTANCE = 3.0f;
        public static float FOCUS_SIZE_FACTOR = 2.0f;

        public static float ORBIT_ROT_SPEED = 0.005f;
        public static float ORBIT_PITCH_LIMIT = 1.5f;
        public static float ORBIT_ZOOM_FACTOR = 0.15f;
        public static float ORBIT_DIST_MIN = 0.5f;
        public static float ORBIT_DIST_MAX = 50.0f;
        public static float ORBIT_DIST_FALLBACK = 5.0f;

        public static float DRAG_SENSITIVITY = 0.02f;
        public static float ROT_DRAG_SPEED = 0.015f;
        public static float SCALE_MIN = 0.1f;

        public static float INIT_OFFSET_X = 3.0f;
        public static float INIT_OFFSET_Y = 3.0f;
        public static float INIT_OFFSET_Z = 3.0f;
        public static float INIT_YAW = -0.75f;
        public static float INIT_PITCH_DEG = 30.0f;

        static void apply() {
            FOCUS_MIN_DISTANCE = getFloat("editor.focusMinDistance", FOCUS_MIN_DISTANCE);
            FOCUS_SIZE_FACTOR = getFloat("editor.focusSizeFactor", FOCUS_SIZE_FACTOR);

            ORBIT_ROT_SPEED = getFloat("editor.orbitRotSpeed", ORBIT_ROT_SPEED);
            ORBIT_PITCH_LIMIT = getFloat("editor.orbitPitchLimit", ORBIT_PITCH_LIMIT);
            ORBIT_ZOOM_FACTOR = getFloat("editor.orbitZoomFactor", ORBIT_ZOOM_FACTOR);
            ORBIT_DIST_MIN = getFloat("editor.orbitDistMin", ORBIT_DIST_MIN);
            ORBIT_DIST_MAX = getFloat("editor.orbitDistMax", ORBIT_DIST_MAX);
            ORBIT_DIST_FALLBACK = getFloat("editor.orbitDistFallback", ORBIT_DIST_FALLBACK);

            DRAG_SENSITIVITY = getFloat("editor.dragSensitivity", DRAG_SENSITIVITY);
            ROT_DRAG_SPEED = getFloat("editor.rotDragSpeed", ROT_DRAG_SPEED);
            SCALE_MIN = getFloat("editor.scaleMin", SCALE_MIN);

            INIT_OFFSET_X = getFloat("editor.initOffsetX", INIT_OFFSET_X);
            INIT_OFFSET_Y = getFloat("editor.initOffsetY", INIT_OFFSET_Y);
            INIT_OFFSET_Z = getFloat("editor.initOffsetZ", INIT_OFFSET_Z);
            INIT_YAW = getFloat("editor.initYaw", INIT_YAW);
            INIT_PITCH_DEG = getFloat("editor.initPitchDeg", INIT_PITCH_DEG);
        }
    }

    // ============================================================
    // Camera
    // ============================================================
    public static final class Camera {
        private Camera() {
        }

        public static float SMOOTH_POS_X = 0.0f;
        public static float SMOOTH_POS_Y = 0.8f;
        public static float SMOOTH_POS_Z = 0.0f;
        public static float CAM_YAW = 0.0f;
        public static float CAM_PITCH = 0.2f;
        public static float CAM_DISTANCE = 5.0f;

        public static float EDITOR_SPEED = 0.2f;
        public static float EDITOR_SPEED_MULTIPLIER = 2.5f;

        public static float PLAY_PITCH_MIN_DEG = -10.0f;
        public static float PLAY_PITCH_MAX_DEG = 85.0f;
        public static float EDIT_PITCH_LIMIT_DEG = 85.0f;

        public static float PLAY_LOOK_SENSITIVITY = 0.0025f;
        public static float EDITOR_LOOK_SENSITIVITY = 0.005f;

        public static float SMOOTH_LERP = 0.15f;
        public static float ORTHO_LERP = 0.12f;
        public static float TARGET_Y_OFFSET = 0.3f;
        public static float EDITOR_ZOOM_SPEED = 0.6f;

        static void apply() {
            SMOOTH_POS_X = getFloat("camera.smoothPosX", SMOOTH_POS_X);
            SMOOTH_POS_Y = getFloat("camera.smoothPosY", SMOOTH_POS_Y);
            SMOOTH_POS_Z = getFloat("camera.smoothPosZ", SMOOTH_POS_Z);
            CAM_YAW = getFloat("camera.yaw", CAM_YAW);
            CAM_PITCH = getFloat("camera.pitch", CAM_PITCH);
            CAM_DISTANCE = getFloat("camera.distance", CAM_DISTANCE);

            EDITOR_SPEED = getFloat("camera.editorSpeed", EDITOR_SPEED);
            EDITOR_SPEED_MULTIPLIER = getFloat("camera.editorSpeedMultiplier", EDITOR_SPEED_MULTIPLIER);

            PLAY_PITCH_MIN_DEG = getFloat("camera.playPitchMinDeg", PLAY_PITCH_MIN_DEG);
            PLAY_PITCH_MAX_DEG = getFloat("camera.playPitchMaxDeg", PLAY_PITCH_MAX_DEG);
            EDIT_PITCH_LIMIT_DEG = getFloat("camera.editPitchLimitDeg", EDIT_PITCH_LIMIT_DEG);

            PLAY_LOOK_SENSITIVITY = getFloat("camera.playLookSensitivity", PLAY_LOOK_SENSITIVITY);
            EDITOR_LOOK_SENSITIVITY = getFloat("camera.editorLookSensitivity", EDITOR_LOOK_SENSITIVITY);

            SMOOTH_LERP = getFloat("camera.smoothLerp", SMOOTH_LERP);
            ORTHO_LERP = getFloat("camera.orthoLerp", ORTHO_LERP);
            TARGET_Y_OFFSET = getFloat("camera.targetYOffset", TARGET_Y_OFFSET);
            EDITOR_ZOOM_SPEED = getFloat("camera.editorZoomSpeed", EDITOR_ZOOM_SPEED);
        }
    }

    // ============================================================
    // Game
    // ============================================================
    public static final class Game {
        private Game() {
        }

        public static float PLAYER_POS_X = 0.0f;
        public static float PLAYER_POS_Y = 0.5f;
        public static float PLAYER_POS_Z = 0.0f;
        public static float PLAYER_SIZE = 1.0f;

        public static float MOVE_SPEED = 0.08f;
        public static float SCALE_LERP = 0.15f;

        public static float JUMP_SCALE_Y = 1.4f;
        public static float JUMP_SCALE_XZ = 0.7f;
        public static float LAND_SCALE_Y = 0.6f;
        public static float LAND_SCALE_XZ = 1.3f;

        /** 각 행: {posX, posY, posZ, sizeX, sizeY, sizeZ} */
        public static float[][] INITIAL_BLOCKS = {
                { 3f, 0f, 0f, 1f, 1f, 1f },
                { 0f, 3f, 0f, 1f, 1f, 1f },
                { 0f, 0f, 3f, 1f, 1f, 1f },
        };

        static void apply() {
            PLAYER_POS_X = getFloat("game.playerPosX", PLAYER_POS_X);
            PLAYER_POS_Y = getFloat("game.playerPosY", PLAYER_POS_Y);
            PLAYER_POS_Z = getFloat("game.playerPosZ", PLAYER_POS_Z);
            PLAYER_SIZE = getFloat("game.playerSize", PLAYER_SIZE);

            MOVE_SPEED = getFloat("game.moveSpeed", MOVE_SPEED);
            SCALE_LERP = getFloat("game.scaleLerp", SCALE_LERP);

            JUMP_SCALE_Y = getFloat("game.jumpScaleY", JUMP_SCALE_Y);
            JUMP_SCALE_XZ = getFloat("game.jumpScaleXZ", JUMP_SCALE_XZ);
            LAND_SCALE_Y = getFloat("game.landScaleY", LAND_SCALE_Y);
            LAND_SCALE_XZ = getFloat("game.landScaleXZ", LAND_SCALE_XZ);

            String raw = get("game.initialBlocks", null);
            if (raw != null) {
                String[] blocks = raw.split(";");
                float[][] out = new float[blocks.length][];
                for (int i = 0; i < blocks.length; i++) {
                    String[] nums = blocks[i].split(",");
                    out[i] = new float[nums.length];
                    for (int j = 0; j < nums.length; j++) {
                        out[i][j] = Float.parseFloat(nums[j].trim());
                    }
                }
                INITIAL_BLOCKS = out;
            }
        }
    }

    // ============================================================
    // Physics
    // ============================================================
    public static final class Physics {
        private Physics() {
        }

        public static float GRAVITY = -0.015f;
        public static float JUMP_STRENGTH = 0.32f;
        public static float VELOCITY_Y = 0.0f;
        public static float SCALE_INIT = 1.0f;

        static void apply() {
            GRAVITY = getFloat("physics.gravity", GRAVITY);
            JUMP_STRENGTH = getFloat("physics.jumpStrength", JUMP_STRENGTH);
            VELOCITY_Y = getFloat("physics.velocityY", VELOCITY_Y);
            SCALE_INIT = getFloat("physics.scaleInit", SCALE_INIT);
        }
    }

    // ============================================================
    // Light
    // ============================================================
    public static final class Light {
        private Light() {
        }

        public static float POS_X = 5.0f;
        public static float POS_Y = 10.0f;
        public static float POS_Z = 5.0f;
        public static float AMBIENT = 0.35f;
        public static float DIFFUSE = 0.8f;

        static void apply() {
            POS_X = getFloat("light.posX", POS_X);
            POS_Y = getFloat("light.posY", POS_Y);
            POS_Z = getFloat("light.posZ", POS_Z);
            AMBIENT = getFloat("light.ambient", AMBIENT);
            DIFFUSE = getFloat("light.diffuse", DIFFUSE);
        }
    }
}