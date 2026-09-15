public class PhysicsState {
    public float velocityY    = EngineConfig.Physics.VELOCITY_Y;
    public boolean isGrounded = false;
    public boolean wasGrounded = true;

    public float scaleX = EngineConfig.Physics.SCALE_INIT;
    public float scaleY = EngineConfig.Physics.SCALE_INIT;
    public float scaleZ = EngineConfig.Physics.SCALE_INIT;
}