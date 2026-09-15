// 에디터/기즈모 상태
public class EditorState {
    public int selectedObjectIndex = -1;
    public int gizmoMode = 0;          // 0:Pos, 1:Scale, 2:Rot
    public int activeGizmoAxis = 0;    // 0:none, 1:X, 2:Y, 3:Z
    public boolean isLocalGizmo = true;
    public boolean prevMousePressed = false;
}