import imgui.type.ImBoolean;

// 키보드/마우스 입력 상태
public class InputState {
    public boolean w, a, s, d, space;
    public double lastMouseX = -1, lastMouseY = -1;
    public boolean isUiMode = false;
    public double mouseDeltaX = 0;
    public double mouseDeltaY = 0;

    public void clearMouseDelta() {
        mouseDeltaX = 0;
        mouseDeltaY = 0;
    }
}