import java.util.EnumMap;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

/**
 * 논리적 Action과 물리적 키/버튼의 매핑.
 *
 * <p>기본 매핑은 코드에 하드코딩되어 있지만, {@link #rebind(Action, int, int)}로
 * 런타임에 변경 가능. ImGui 설정 패널이나 프로퍼티 파일과 연동 가능.
 *
 * <p>키 코드는 GLFW 상수 (GLFW_KEY_*, GLFW_MOUSE_BUTTON_*).
 * 마우스 버튼은 {@link #MOUSE_OFFSET}을 더해 키 공간과 분리.
 */
public class InputMap {
    /** 마우스 버튼을 키 코드와 구분하기 위한 오프셋. */
    public static final int MOUSE_OFFSET = 0x10000;

    /** 액션 → 키 코드 (키보드: GLFW_KEY_*, 마우스: MOUSE_OFFSET + GLFW_MOUSE_BUTTON_*). */
    private final Map<Action, Integer> bindings = new EnumMap<>(Action.class);

    public InputMap() {
        setDefaults();
    }

    /** 기본 매핑. */
    public void setDefaults() {
        bindings.clear();

        bind(Action.MOVE_FORWARD,      GLFW_KEY_W);
        bind(Action.MOVE_BACKWARD,     GLFW_KEY_S);
        bind(Action.MOVE_LEFT,         GLFW_KEY_A);
        bind(Action.MOVE_RIGHT,        GLFW_KEY_D);
        bind(Action.JUMP,              GLFW_KEY_SPACE);

        bind(Action.FLY_UP,            GLFW_KEY_E);
        bind(Action.FLY_DOWN,          GLFW_KEY_Q);
        bind(Action.FLY_FAST,          GLFW_KEY_LEFT_CONTROL);

        bind(Action.GIZMO_POSITION,    GLFW_KEY_1);
        bind(Action.GIZMO_SCALE,       GLFW_KEY_2);
        bind(Action.GIZMO_ROTATION,    GLFW_KEY_3);
        bind(Action.GIZMO_FOCUS,       GLFW_KEY_F);
        bind(Action.GIZMO_TOGGLE_LOCAL,GLFW_KEY_L);

        bind(Action.ORBIT_MODIFIER,    GLFW_KEY_LEFT_ALT);

        bind(Action.MOUSE_LEFT,        MOUSE_OFFSET + GLFW_MOUSE_BUTTON_LEFT);
        bind(Action.MOUSE_RIGHT,       MOUSE_OFFSET + GLFW_MOUSE_BUTTON_RIGHT);
        bind(Action.MOUSE_MIDDLE,      MOUSE_OFFSET + GLFW_MOUSE_BUTTON_MIDDLE);

        bind(Action.TOGGLE_UI_MODE,    GLFW_KEY_TAB);
    }

    public void bind(Action action, int keyCode) {
        bindings.put(action, keyCode);
    }

    public void unbind(Action action) {
        bindings.remove(action);
    }

    /** 액션에 매핑된 키 코드. 미매핑 시 -1. */
    public int getKey(Action action) {
        return bindings.getOrDefault(action, -1);
    }

    /** 키 코드가 마우스 버튼인지. */
    public static boolean isMouseButton(int keyCode) {
        return keyCode >= MOUSE_OFFSET;
    }

    /** 마우스 버튼 코드에서 순수 GLFW 버튼 상수 추출. */
    public static int toMouseButton(int keyCode) {
        return keyCode - MOUSE_OFFSET;
    }

    // ============================================================
    // 복수 매핑 (선택) — 하나의 액션에 여러 키
    // ============================================================
    // 현재는 단일 매핑만 지원. 확장 시 Map<Action, List<Integer>>로.
}