import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public class EngineInputSystem {
    private final Engine3DLWJGL engine;

    public EngineInputSystem(Engine3DLWJGL engine) {
        this.engine = engine;
    }

    public void configureCallbacks() {
        glfwSetKeyCallback(engine.state.window, (win, key, scancode, action, mods) -> {
            boolean isPressed = (action != GLFW_RELEASE);
            if (key == GLFW_KEY_W) engine.state.w = isPressed;
            if (key == GLFW_KEY_A) engine.state.a = isPressed;
            if (key == GLFW_KEY_S) engine.state.s = isPressed;
            if (key == GLFW_KEY_D) engine.state.d = isPressed;
            if (key == GLFW_KEY_SPACE) engine.state.space = isPressed;

            if (key == GLFW_KEY_TAB && action == GLFW_PRESS) {
                engine.state.isUiMode = !engine.state.isUiMode;
                glfwSetInputMode(engine.state.window, GLFW_CURSOR, engine.state.isUiMode ? GLFW_CURSOR_NORMAL : GLFW_CURSOR_DISABLED);
                engine.state.lastMouseX = -1;
                engine.state.lastMouseY = -1;
            }
        });

        glfwSetInputMode(engine.state.window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
        glfwSetCursorPosCallback(engine.state.window, (win, xpos, ypos) -> {
            if (engine.state.lastMouseX == -1) {
                engine.state.lastMouseX = xpos;
                engine.state.lastMouseY = ypos;
            }

            if (!engine.state.isUiMode) {
                engine.state.mouseDeltaX += xpos - engine.state.lastMouseX;
                engine.state.mouseDeltaY += ypos - engine.state.lastMouseY;
            }

            engine.state.lastMouseX = xpos;
            engine.state.lastMouseY = ypos;
        });
    }
}
