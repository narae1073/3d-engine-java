import imgui.ImGui;
import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Engine3DLWJGL {
    public final EngineState state = new EngineState();
    private final EngineInputSystem inputSystem = new EngineInputSystem(this);
    private final EngineRenderer renderer = new EngineRenderer(this);
    private final EngineUiSystem uiSystem = new EngineUiSystem(this);
    private final EngineCollisionSystem collisionSystem = new EngineCollisionSystem(this);
    private final EngineGameSystem gameSystem = new EngineGameSystem(this);
    // [추가] EditorGizmoSystem 인스턴스 생성
    private final EditorGizmoSystem gizmoSystem = new EditorGizmoSystem(this);

    public long getWindow() {
        return state.window;
    }

    public void run() {
        init();
        loop();
        cleanup();
    }

    private void init() {
        if (!glfwInit())
            throw new IllegalStateException("GLFW 초기화 실패");

        state.window = glfwCreateWindow(state.width, state.height, "3D Engine - Shadows & Slime Integrated", NULL, NULL);
        glfwMakeContextCurrent(state.window);
        glfwSwapInterval(1);
        GL.createCapabilities();

        ImGui.createContext();
        state.imGuiGlfw.init(state.window, true);
        state.imGuiGl3.init("#version 120");

        inputSystem.configureCallbacks();
        glEnable(GL_DEPTH_TEST);

        if (glfwRawMouseMotionSupported()) {
            glfwSetInputMode(state.window, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE);
        }

        gameSystem.initWorld();
        renderer.initShadowFBO();
        renderer.initShaders();
    }

    private void update() {
        gameSystem.update();
        gizmoSystem.update();
    }

    private void render() {
        renderer.render();
        uiSystem.renderImGui();
    }

    private void loop() {
        while (!glfwWindowShouldClose(state.window)) {
            update();
            render();
            glfwSwapBuffers(state.window);
            glfwPollEvents();
        }
    }

    private void cleanup() {
        renderer.cleanup();
        state.imGuiGl3.dispose();
        state.imGuiGlfw.dispose();
        ImGui.destroyContext();

        glfwDestroyWindow(state.window);
        glfwTerminate();
    }

    public static void main(String[] args) {
        new Engine3DLWJGL().run();
    }
}
