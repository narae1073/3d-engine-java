import imgui.type.ImBoolean;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 입력 상태.
 *
 * <p>매 프레임 {@link #poll(long, InputMap)}을 호출해
 * 모든 액션의 다운 상태를 캐시한다. 이후 시스템들은
 * {@link #isDown(Action)} / {@link #isPressed(Action)} / {@link #isReleased(Action)}로
 * 질의한다.
 *
 * <p>키/버튼 이벤트(다운/업 전환)는 이전 프레임 상태와 비교해 계산.
 */
public class InputState {
    private final Map<Action, Boolean> down = new EnumMap<>(Action.class);
    private final Map<Action, Boolean> prevDown = new EnumMap<>(Action.class);

    /** 이번 프레임에 눌린(다운 전환) 액션. */
    private final Set<Action> pressed = EnumSet.noneOf(Action.class);
    /** 이번 프레임에 뗀(업 전환) 액션. */
    private final Set<Action> released = EnumSet.noneOf(Action.class);

    // --- 마우스 ---
    public double mouseX = 0, mouseY = 0;
    public double mouseDeltaX = 0, mouseDeltaY = 0;
    public double scrollDelta = 0;

    // --- UI 모드 ---
    public boolean isUiMode = false;
    public double lastMouseX = -1, lastMouseY = -1;

    // ============================================================
    // 폴링
    // ============================================================
    /**
     * 매 프레임 시작 시 1회 호출.
     * 모든 액션의 키 상태를 폴링해 캐시한다.
     */
    public void poll(long window, InputMap map) {
        prevDown.clear();
        prevDown.putAll(down);
        down.clear();
        pressed.clear();
        released.clear();

        for (Action action : Action.values()) {
            int key = map.getKey(action);
            if (key < 0) continue;

            boolean isDown = pollKey(window, key);
            down.put(action, isDown);

            boolean wasDown = Boolean.TRUE.equals(prevDown.get(action));
            if (isDown && !wasDown) pressed.add(action);
            if (!isDown && wasDown) released.add(action);
        }
    }

    /** 키 또는 마우스 버튼의 다운 상태 조회. Left/Right 쌍 자동 인식. */
    private static boolean pollKey(long window, int keyCode) {
        if (InputMap.isMouseButton(keyCode)) {
            int btn = InputMap.toMouseButton(keyCode);
            return org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, btn)
                    == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        }
        // 키보드: Left/Right 쌍 자동 인식
        int mirror = mirrorKey(keyCode);
        boolean primary = org.lwjgl.glfw.GLFW.glfwGetKey(window, keyCode)
                == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        if (mirror < 0) return primary;
        boolean secondary = org.lwjgl.glfw.GLFW.glfwGetKey(window, mirror)
                == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        return primary || secondary;
    }

    /** Left/Right 쌍의 반대편 키. 없으면 -1. */
    private static int mirrorKey(int keyCode) {
        switch (keyCode) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL:  return org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL: return org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT:      return org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT:     return org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT:    return org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT:   return org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SUPER:    return org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SUPER;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SUPER:   return org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SUPER;
            default: return -1;
        }
    }

    // ============================================================
    // 질의 API
    // ============================================================
    public boolean isDown(Action action) {
        return Boolean.TRUE.equals(down.get(action));
    }

    public boolean isPressed(Action action) {
        return pressed.contains(action);
    }

    public boolean isReleased(Action action) {
        return released.contains(action);
    }

    // ============================================================
    // 마우스 / 프레임 경계
    // ============================================================
    public void clearMouseDelta() {
        mouseDeltaX = 0;
        mouseDeltaY = 0;
        scrollDelta = 0;
    }
}