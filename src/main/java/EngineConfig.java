/**
 * 엔진 전역 설정 상수.
 *
 * <p>모든 매직 넘버를 한 곳에 모아 튜닝 지점을 단일화한다.
 * 인스턴스화하지 않으며, 모든 필드는 {@code static final}이다.
 *
 * <p>주의: {@link org.joml.Vector3f} 같은 가변 객체는 {@code static final}로
 * 두면 내용이 변할 수 있으므로, 벡터 기본값은 {@code float[]} 또는 개별 float로
 * 정의한다. 사용하는 쪽에서 {@code new Vector3f(EngineConfig.X, Y, Z)}로 복사해 쓴다.
 */
public final class EngineConfig {
    private EngineConfig() {}

    // ============================================================
    // 윈도우
    // ============================================================
    public static final class Window {
        private Window() {}
        public static final int   WIDTH  = 1920;
        public static final int   HEIGHT = 1080;
        public static final String TITLE = "3D Engine - Shadows & Slime Integrated";
        public static final int   SWAP_INTERVAL = 1;
    }

    // ============================================================
    // 엔진 루프
    // ============================================================
    public static final class Engine {
        private Engine() {}
        /** 프레임 스파이크 방지용 dt 상한 (초). */
        public static final float DT_CLAMP = 0.05f;
        /** ImGui GLSL 버전 문자열. */
        public static final String GLSL_VERSION = "#version 120";
    }

    // ============================================================
    // 렌더러
    // ============================================================
    public static final class Render {
        private Render() {}

        // --- 셰도우 맵 ---
        // 컴파일 타임 상수임. 런타임에 바꾸고 싶다면 static (non-final)로 바꾸거나, 인스턴스 필드로 승격해야 함
        public static final int SHADOW_WIDTH  = 2048;
        public static final int SHADOW_HEIGHT = 2048;

        // --- 라이트 공간 (직교) ---
        public static final float LIGHT_NEAR       = 1.0f;
        public static final float LIGHT_FAR        = 40.0f;
        public static final float LIGHT_ORTHO_HALF = 15.0f;

        // --- 메인 투영 ---
        public static final float CAM_FOV_DEG      = 60.0f;
        public static final float CAM_NEAR         = 0.1f;
        public static final float CAM_FAR          = 100.0f;
        public static final float ORTHO_HALF_SIZE  = 6.0f;

        // --- 클리어 컬러 ---
        public static final float CLEAR_R = 0.53f;
        public static final float CLEAR_G = 0.81f;
        public static final float CLEAR_B = 0.92f;
        public static final float CLEAR_A = 1.0f;

        // --- 지면 / 그리드 ---
        public static final float GROUND_HALF     = 30.0f;
        public static final int   GRID_HALF_COUNT = 30;
        public static final float GRID_LINE_Y     = 0.001f; // z-fighting 방지용 y 오프셋

        // --- 스피어 ---
        public static final int   SPHERE_STACKS = 20;
        public static final int   SPHERE_SLICES = 20;
        public static final float SPHERE_RADIUS = 0.5f;

        // --- 큐브 ---
        public static final float CUBE_HALF = 0.5f;

        // --- 오브젝트 컬러 ---
        public static final float[] COLOR_GROUND        = { 0.50f, 0.50f, 0.50f };
        public static final float[] COLOR_GRID          = { 0.00f, 0.00f, 0.00f };
        public static final float[] COLOR_PLAYER_GROUND = { 0.20f, 0.90f, 1.00f };
        public static final float[] COLOR_PLAYER_AIR    = { 0.10f, 0.60f, 1.00f };
        public static final float[] COLOR_BLOCK         = { 0.95f, 0.50f, 0.20f };
        public static final float[] COLOR_HIGHLIGHT     = { 1.00f, 1.00f, 0.00f };

        /** 하이라이트 박스 크기 배율. */
        public static final float HIGHLIGHT_SCALE = 1.02f;

        // --- 라인 두께 ---
        public static final float LINE_WIDTH_DEFAULT   = 1.0f;
        public static final float LINE_WIDTH_HIGHLIGHT = 4.0f;
        public static final float LINE_WIDTH_POSITION  = 5.0f;
        public static final float LINE_WIDTH_SCALE_ROT = 3.0f;
    }

    // ============================================================
    // 기즈모 (렌더 + 피킹 공통)
    // ============================================================
    public static final class Gizmo {
        private Gizmo() {}

        // --- 축 길이 ---
        public static final float AXIS_LENGTH  = 2.0f;
        public static final float AXIS_THICKNESS = 0.3f;

        // --- 스케일 핸들 큐브 ---
        public static final float HANDLE_CUBE_SIZE = 0.3f;

        // --- 회전 링 ---
        public static final float RING_RADIUS   = 2.0f;
        public static final float RING_THICKNESS = 0.3f;
        public static final int   RING_SEGMENTS = 32;

        // --- 피킹 ---
        public static final float PICK_EPSILON = 1e-6f;
    }

    // ============================================================
    // 에디터 / 기즈모 조작
    // ============================================================
    public static final class Editor {
        private Editor() {}

        // --- Focus (F키) ---
        public static final float FOCUS_MIN_DISTANCE = 3.0f;
        public static final float FOCUS_SIZE_FACTOR  = 2.0f;

        // --- Orbit (Alt + 우클릭) ---
        public static final float ORBIT_ROT_SPEED     = 0.005f;
        public static final float ORBIT_PITCH_LIMIT   = 1.5f;
        public static final float ORBIT_ZOOM_FACTOR   = 0.15f;
        public static final float ORBIT_DIST_MIN      = 0.5f;
        public static final float ORBIT_DIST_MAX      = 50.0f;
        public static final float ORBIT_DIST_FALLBACK = 5.0f;

        // --- 드래그 ---
        public static final float DRAG_SENSITIVITY = 0.02f;
        public static final float ROT_DRAG_SPEED   = 0.015f;
        public static final float SCALE_MIN        = 0.1f;

        // --- 초기 카메라 ---
        public static final float INIT_OFFSET_X = 3.0f;
        public static final float INIT_OFFSET_Y = 3.0f;
        public static final float INIT_OFFSET_Z = 3.0f;
        public static final float INIT_YAW      = -0.75f;
        public static final float INIT_PITCH_DEG = 30.0f;
    }

    // ============================================================
    // 카메라 (기본값)
    // ============================================================
    public static final class Camera {
        private Camera() {}

        // 플레이
        public static final float SMOOTH_POS_X = 0.0f;
        public static final float SMOOTH_POS_Y = 0.8f;
        public static final float SMOOTH_POS_Z = 0.0f;
        public static final float CAM_YAW      = 0.0f;
        public static final float CAM_PITCH    = 0.2f;
        public static final float CAM_DISTANCE = 5.0f;

        // 에디터
        public static final float EDITOR_SPEED            = 0.2f;
        public static final float EDITOR_SPEED_MULTIPLIER = 2.5f;

        // 플레이 시점 클램프
        public static final float PLAY_PITCH_MIN_DEG = -10.0f;
        public static final float PLAY_PITCH_MAX_DEG =  85.0f;

        // 에디터 시점 클램프
        public static final float EDIT_PITCH_LIMIT_DEG = 85.0f;

        // 마우스 감도
        public static final float PLAY_LOOK_SENSITIVITY   = 0.0025f;
        public static final float EDITOR_LOOK_SENSITIVITY = 0.005f;

        // 카메라 lerp 계수
        public static final float SMOOTH_LERP   = 0.15f;
        public static final float ORTHO_LERP    = 0.12f;
        public static final float TARGET_Y_OFFSET = 0.3f;

        // 에디터 줌
        public static final float EDITOR_ZOOM_SPEED = 0.6f;
    }

    // ============================================================
    // 플레이어 게임 로직
    // ============================================================
    public static final class Game {
        private Game() {}

        // 초기 플레이어
        public static final float PLAYER_POS_X = 0.0f;
        public static final float PLAYER_POS_Y = 0.5f;
        public static final float PLAYER_POS_Z = 0.0f;
        public static final float PLAYER_SIZE  = 1.0f;

        // 이동
        public static final float MOVE_SPEED = 0.08f;

        // 슬라임 스케일 lerp
        public static final float SCALE_LERP = 0.15f;

        // 점프 squash/stretch
        public static final float JUMP_SCALE_Y   = 1.4f;
        public static final float JUMP_SCALE_XZ  = 0.7f;
        public static final float LAND_SCALE_Y   = 0.6f;
        public static final float LAND_SCALE_XZ  = 1.3f;

        // 초기 블록들 (위치, 크기)
        public static final float[][] INITIAL_BLOCKS = {
            // { posX, posY, posZ, sizeX, sizeY, sizeZ }
            { 3f, 0f, 0f, 1f, 1f, 1f },
            { 0f, 3f, 0f, 1f, 1f, 1f },
            { 0f, 0f, 3f, 1f, 1f, 1f },
        };
    }

    // ============================================================
    // 물리
    // ============================================================
    public static final class Physics {
        private Physics() {}
        public static final float GRAVITY       = -0.015f;
        public static final float JUMP_STRENGTH =  0.32f;
        public static final float VELOCITY_Y    =  0.0f;
        public static final float SCALE_INIT    =  1.0f;
    }

    // ============================================================
    // 조명
    // ============================================================
    public static final class Light {
        private Light() {}
        public static final float POS_X   = 5.0f;
        public static final float POS_Y   = 10.0f;
        public static final float POS_Z   = 5.0f;
        public static final float AMBIENT = 0.35f;
        public static final float DIFFUSE = 0.8f;
    }
}