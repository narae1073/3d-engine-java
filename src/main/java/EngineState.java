import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;

/**
 * 엔진 전역 상태의 얇은 컨테이너.
 * 실제 데이터는 책임별 컴포넌트로 분리되어 있고,
 * 여기서는 그것들을 조합하고 ImGui 백엔드만 소유한다.
 */
public class EngineState {
    // ImGui 백엔드 (라이프사이클이 엔진과 동일하므로 여기 소유)
    public final ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();
    public final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();

    // 책임별 컴포넌트 (final → 조합 관계 고정)
    public final WindowContext window = new WindowContext();
    public final InputState input = new InputState();
    public final CameraState camera = new CameraState();
    public final PhysicsState physics = new PhysicsState();
    public final EditorState editor = new EditorState();
    public final WorldState world = new WorldState();
}