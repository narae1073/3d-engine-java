// 게임/에디터가 인식하는 논리적 액션
public enum Action {
    // --- 플레이 ---
    MOVE_FORWARD,
    MOVE_BACKWARD,
    MOVE_LEFT,
    MOVE_RIGHT,
    JUMP,

    // --- 에디터 자유 비행 ---
    FLY_UP,          // E
    FLY_DOWN,        // Q
    FLY_FAST,        // Ctrl (수정키)

    // --- 에디터 기즈모 ---
    GIZMO_POSITION,  // 1
    GIZMO_SCALE,     // 2
    GIZMO_ROTATION,  // 3
    GIZMO_FOCUS,     // F
    GIZMO_TOGGLE_LOCAL, // L

    // --- 에디터 카메라 ---
    ORBIT_MODIFIER,  // Alt (수정키)

    // --- 마우스 ---
    MOUSE_LEFT,
    MOUSE_RIGHT,
    MOUSE_MIDDLE,

    // --- 시스템 ---
    TOGGLE_UI_MODE,  // Tab
    TOGGLE_EDITOR_MODE // (선택)
}