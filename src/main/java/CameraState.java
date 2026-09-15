import imgui.type.ImBoolean;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class CameraState {
    // --- 플레이 모드 ---
    public final Vector3f smoothCamPos = new Vector3f(
            EngineConfig.Camera.SMOOTH_POS_X,
            EngineConfig.Camera.SMOOTH_POS_Y,
            EngineConfig.Camera.SMOOTH_POS_Z);
    public float camYaw      = EngineConfig.Camera.CAM_YAW;
    public float camPitch    = EngineConfig.Camera.CAM_PITCH;
    public final ImBoolean isOrthographic = new ImBoolean(false);
    public float orthoTransition = 0.0f;

    // --- 에디터 모드 ---
    public final ImBoolean isEditorMode = new ImBoolean(false);
    public final Vector3f editorCamPos = new Vector3f();
    public float editorCamYaw   = 0.0f;
    public float editorCamPitch = 0.0f;
    public boolean hasInitializedEditorCam = false;

    public final Matrix4f editorInvVPMatrix = new Matrix4f();
}