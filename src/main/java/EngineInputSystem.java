import static org.lwjgl.glfw.GLFW.*;

public class EngineInputSystem implements EngineSystem {
    private final WindowContext win;
    private final InputState input;

    public EngineInputSystem(WindowContext win, InputState input) {
        this.win = win;
        this.input = input;
    }

    @Override
    public void init() {
        glfwSetKeyCallback(win.window, (w, key, sc, action, mods) -> {
            boolean isPressed = (action != GLFW_RELEASE);
            if (key == GLFW_KEY_W) input.w = isPressed;
            if (key == GLFW_KEY_A) input.a = isPressed;
            if (key == GLFW_KEY_S) input.s = isPressed;
            if (key == GLFW_KEY_D) input.d = isPressed;
            if (key == GLFW_KEY_SPACE) input.space = isPressed;

            if (key == GLFW_KEY_TAB && action == GLFW_PRESS) {
                input.isUiMode = !input.isUiMode;
                glfwSetInputMode(win.window, GLFW_CURSOR,
                        input.isUiMode ? GLFW_CURSOR_NORMAL : GLFW_CURSOR_DISABLED);
                input.lastMouseX = -1;
                input.lastMouseY = -1;
            }
        });

        glfwSetInputMode(win.window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        glfwSetCursorPosCallback(win.window, (w, xpos, ypos) -> {
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
    }
}