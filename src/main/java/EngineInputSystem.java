import imgui.ImGui;

import static org.lwjgl.glfw.GLFW.*;

public class EngineInputSystem implements EngineSystem {
    private final WindowContext win;
    private final InputState input;
    private final InputMap inputMap;

    public EngineInputSystem(WindowContext win, InputState input, InputMap inputMap) {
        this.win = win;
        this.input = input;
        this.inputMap = inputMap;
    }

    @Override
    public void init() {
        // 마우스 커서 위치 콜백 (델타 + 절대 좌표)
        glfwSetCursorPosCallback(win.window, (w, xpos, ypos) -> {
            input.mouseX = xpos;
            input.mouseY = ypos;

            if (input.lastMouseX == -1) {
                input.lastMouseX = xpos;
                input.lastMouseY = ypos;
            }
            if (!input.isUiMode) {
                input.mouseDeltaX += xpos - input.lastMouseX;
                input.mouseDeltaY += ypos - input.lastMouseY;
            }
            input.lastMouseX = xpos;
            input.lastMouseY = ypos;
        });

        // 스크롤 콜백
        glfwSetScrollCallback(win.window, (w, xoff, yoff) -> {
            input.scrollDelta += yoff;
        });

        glfwSetInputMode(win.window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
    }

    @Override
    public void update(float dt) {
        // 매 프레임 키/버튼 상태 폴링
        input.poll(win.window, inputMap);

        // TAB: UI 모드 토글 (poll 후 pressed 판정)
        if (input.isPressed(Action.TOGGLE_UI_MODE)) {
            input.isUiMode = !input.isUiMode;
            glfwSetInputMode(win.window, GLFW_CURSOR,
                    input.isUiMode ? GLFW_CURSOR_NORMAL : GLFW_CURSOR_DISABLED);
            input.lastMouseX = -1;
            input.lastMouseY = -1;
        }
    }
}