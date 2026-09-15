public class PhysicsState {
    public float velocityY    = EngineConfig.Physics.VELOCITY_Y;
    public float gravity      = EngineConfig.Physics.GRAVITY;
    public float jumpStrength = EngineConfig.Physics.JUMP_STRENGTH;
    public boolean isGrounded = false;
    public boolean wasGrounded = true;

    public float scaleX = EngineConfig.Physics.SCALE_INIT;
    public float scaleY = EngineConfig.Physics.SCALE_INIT;
    public float scaleZ = EngineConfig.Physics.SCALE_INIT;
}