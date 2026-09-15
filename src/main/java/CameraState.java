import imgui.type.ImBoolean;
import org.joml.Matrix4f;
import org.joml.Vector3f;

// 플레이 카메라 + 에디터 카메라 통합
public class CameraState {
    // --- 플레이 모드 ---
    public final Vector3f smoothCamPos = new Vector3f(0.0f, 0.8f, 0.0f);
    public float camYaw = 0.0f, camPitch = 0.2f;
    public float camDistance = 5.0f;
    public final ImBoolean isOrthographic = new ImBoolean(false);
    public float orthoTransition = 0.0f;

    // --- 에디터 모드 ---
    public final ImBoolean isEditorMode = new ImBoolean(false);
    public final Vector3f editorCamPos = new Vector3f();
    public float editorCamYaw = 0.0f;
    public float editorCamPitch = 0.0f;
    public boolean hasInitializedEditorCam = false;
    public float editorSpeed = 0.2f;
    public float editorSpeedMultiplier = 2.5f;

    // 역 VP 행렬 (기즈모 피킹용)
    public final Matrix4f editorInvVPMatrix = new Matrix4f();
}