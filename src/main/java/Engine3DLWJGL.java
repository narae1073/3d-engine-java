import imgui.ImGui;
import org.lwjgl.opengl.GL;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Engine3DLWJGL {
    public final EngineState state = new EngineState();

    // 컴포넌트 DI를 위한 편의 접근 (선택)
    private final WindowContext win = state.window;
    private final InputState input = state.input;
    private final CameraState camera = state.camera;
    private final LightState light = state.light;
    private final PhysicsState physics = state.physics;
    private final EditorState editor = state.editor;
    private final WorldState world = state.world;

    private final EngineCollisionSystem collisionSystem = new EngineCollisionSystem(world);

    private final List<EngineSystem> systems = new ArrayList<>();
    private double lastFrameTime = 0.0;

    public Engine3DLWJGL() {
        // 의존성 명시적 주입 — 각 시스템이 필요한 컴포넌트만 받음
        systems.add(new EngineInputSystem(win, input));
        systems.add(new EngineGameSystem(win, input, camera, physics, world, collisionSystem));
        systems.add(new EngineRenderer(win, camera, light, physics, editor, world));
        systems.add(new EngineUiSystem(state, camera, light, physics, editor, world));
        systems.add(new EditorGizmoSystem(win, camera, editor, world));
    }

    public void run() { init(); loop(); cleanup(); }

    private void init() {
        if (!glfwInit()) throw new IllegalStateException("GLFW 초기화 실패");
        win.window = glfwCreateWindow(win.width, win.height,
                "3D Engine - Shadows & Slime Integrated", NULL, NULL);
        glfwMakeContextCurrent(win.window);
        glfwSwapInterval(1);
        GL.createCapabilities();

        ImGui.createContext();
        state.imGuiGlfw.init(win.window, true);
        state.imGuiGl3.init("#version 120");
        glEnable(GL_DEPTH_TEST);
        if (glfwRawMouseMotionSupported())
            glfwSetInputMode(win.window, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE);

        for (EngineSystem s : systems) s.init();
    }

    private void update() {
        float dt = computeDeltaTime();
        for (EngineSystem s : systems) s.update(dt);
    }

    private void render() {
        for (EngineSystem s : systems) s.render();
    }

    private void loop() {
        while (!glfwWindowShouldClose(win.window)) {
            update(); render();
            glfwSwapBuffers(win.window);
            glfwPollEvents();
        }
    }

    private void cleanup() {
        for (int i = systems.size() - 1; i >= 0; i--) systems.get(i).dispose();
        state.imGuiGl3.dispose();
        state.imGuiGlfw.dispose();
        ImGui.destroyContext();
        glfwDestroyWindow(win.window);
        glfwTerminate();
    }

    private float computeDeltaTime() {
        double now = glfwGetTime();
        float dt = (float) (now - lastFrameTime);
        lastFrameTime = now;
        return Math.min(dt, 0.05f);
    }

    public static void main(String[] args) { new Engine3DLWJGL().run(); }
}