// 플레이어 물리 + 슬라임 스케일 애니메이션
public class PhysicsState {
    public float velocityY = 0.0f;
    public float gravity = -0.015f;
    public float jumpStrength = 0.32f;
    public boolean isGrounded = false;
    public boolean wasGrounded = true;

    // 슬라임 애니메이션
    public float scaleX = 1.0f, scaleY = 1.0f, scaleZ = 1.0f;
}