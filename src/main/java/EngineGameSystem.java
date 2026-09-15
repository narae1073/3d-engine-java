import org.joml.Vector3f;
import static org.lwjgl.glfw.GLFW.*;

public class EngineGameSystem implements EngineSystem {
    private final WindowContext win;
    private final InputState input;
    private final CameraState camera;
    private final PhysicsState physics;
    private final WorldState world;
    private final EngineCollisionSystem collisionSystem;

    public EngineGameSystem(WindowContext win, InputState input, CameraState camera,
            PhysicsState physics, WorldState world,
            EngineCollisionSystem collisionSystem) {
        this.win = win;
        this.input = input;
        this.camera = camera;
        this.physics = physics;
        this.world = world;
        this.collisionSystem = collisionSystem;
    }

    @Override
    public void init() {
        initWorld();
    }

    private void initWorld() {
        world.playerObject = new PlayerObject(
                new Vector3f(EngineConfig.Game.PLAYER_POS_X,
                        EngineConfig.Game.PLAYER_POS_Y,
                        EngineConfig.Game.PLAYER_POS_Z),
                new Vector3f(EngineConfig.Game.PLAYER_SIZE,
                        EngineConfig.Game.PLAYER_SIZE,
                        EngineConfig.Game.PLAYER_SIZE));
        world.objects.add(world.playerObject);

        for (float[] b : EngineConfig.Game.INITIAL_BLOCKS) {
            world.objects.add(new BlockObject(
                    new Vector3f(b[0], b[1], b[2]),
                    new Vector3f(b[3], b[4], b[5])));
        }

        camera.smoothCamPos.set(
                world.playerObject.pos.x,
                world.playerObject.pos.y + EngineConfig.Camera.TARGET_Y_OFFSET,
                world.playerObject.pos.z);
    }

    @Override
    public void update(float dt) {
        if (camera.isEditorMode.get()) {
            updateEditorMode(dt);
        } else {
            updatePlayMode(dt);
        }

        // ★ ortho transition lerp: 프레임 독립
        float ref = EngineConfig.Engine.REFERENCE_FPS;
        float frameScale = dt * ref;
        float lerp = frameIndependentLerp(EngineConfig.Camera.ORTHO_LERP, frameScale);

        float targetTransition = camera.isOrthographic.get() ? 1.0f : 0.0f;
        camera.orthoTransition += (targetTransition - camera.orthoTransition) * lerp;
    }

    public void updateEditorMode(float dt) {

        if (!camera.hasInitializedEditorCam) {
            camera.editorCamPos.set(
                    world.playerObject.pos.x + EngineConfig.Editor.INIT_OFFSET_X,
                    world.playerObject.pos.y + EngineConfig.Editor.INIT_OFFSET_Y,
                    world.playerObject.pos.z + EngineConfig.Editor.INIT_OFFSET_Z);
            camera.editorCamYaw = EngineConfig.Editor.INIT_YAW;
            camera.editorCamPitch = (float) Math.toRadians(EngineConfig.Editor.INIT_PITCH_DEG);
            camera.hasInitializedEditorCam = true;
        }

        boolean isFlyActive = input.isDown(Action.MOUSE_MIDDLE)
                || (input.isDown(Action.MOUSE_RIGHT) && !input.isDown(Action.ORBIT_MODIFIER));

        if (isFlyActive) {
            float sensitivity = EngineConfig.Camera.EDITOR_LOOK_SENSITIVITY;
            camera.editorCamYaw += imgui.ImGui.getIO().getMouseDeltaX() * sensitivity;
            camera.editorCamPitch += imgui.ImGui.getIO().getMouseDeltaY() * sensitivity;
            float limit = (float) Math.toRadians(EngineConfig.Camera.EDIT_PITCH_LIMIT_DEG);
            camera.editorCamPitch = Math.max(-limit, Math.min(limit, camera.editorCamPitch));
        }

        float forwardX = (float) (Math.sin(camera.editorCamYaw) * Math.cos(camera.editorCamPitch));
        float forwardY = (float) -Math.sin(camera.editorCamPitch);
        float forwardZ = (float) (-Math.cos(camera.editorCamYaw) * Math.cos(camera.editorCamPitch));
        float rightX = (float) Math.cos(camera.editorCamYaw);
        float rightZ = (float) Math.sin(camera.editorCamYaw);

        float frameScale = dt * EngineConfig.Engine.REFERENCE_FPS;
        boolean isCtrl = input.isDown(Action.FLY_FAST);
        float currentSpeed = EngineConfig.Camera.EDITOR_SPEED
                * (isCtrl ? EngineConfig.Camera.EDITOR_SPEED_MULTIPLIER : 1.0f)
                * frameScale;

        Vector3f editorMoveDir = new Vector3f();

        if (isFlyActive) {
            if (input.isDown(Action.MOVE_FORWARD))
                editorMoveDir.add(forwardX, forwardY, forwardZ);
            if (input.isDown(Action.MOVE_BACKWARD))
                editorMoveDir.sub(forwardX, forwardY, forwardZ);
            if (input.isDown(Action.MOVE_LEFT))
                editorMoveDir.sub(rightX, 0, rightZ);
            if (input.isDown(Action.MOVE_RIGHT))
                editorMoveDir.add(rightX, 0, rightZ);
        } else {
            float flatForwardX = (float) Math.sin(camera.editorCamYaw);
            float flatForwardZ = (float) -Math.cos(camera.editorCamYaw);
            if (input.isDown(Action.MOVE_FORWARD))
                editorMoveDir.add(flatForwardX, 0, flatForwardZ);
            if (input.isDown(Action.MOVE_BACKWARD))
                editorMoveDir.sub(flatForwardX, 0, flatForwardZ);
            if (input.isDown(Action.MOVE_LEFT))
                editorMoveDir.sub(rightX, 0, rightZ);
            if (input.isDown(Action.MOVE_RIGHT))
                editorMoveDir.add(rightX, 0, rightZ);
            if (input.isDown(Action.FLY_UP))
                camera.editorCamPos.y += currentSpeed;
            if (input.isDown(Action.FLY_DOWN))
                camera.editorCamPos.y -= currentSpeed;
        }

        if (editorMoveDir.lengthSquared() > 0) {
            editorMoveDir.normalize().mul(currentSpeed);
            camera.editorCamPos.add(editorMoveDir);
        }

        float wheel = (float) input.scrollDelta; // ★ GLFW 콜백 값
        if (wheel != 0) {
            float zoomSpeed = EngineConfig.Camera.EDITOR_ZOOM_SPEED;
            camera.editorCamPos.add(forwardX * wheel * zoomSpeed,
                    forwardY * wheel * zoomSpeed,
                    forwardZ * wheel * zoomSpeed);
        }

        camera.smoothCamPos.set(camera.editorCamPos);
        camera.camYaw = camera.editorCamYaw;
        camera.camPitch = camera.editorCamPitch;
    }

    public void updatePlayMode(float dt) {
        float frameScale = dt * EngineConfig.Engine.REFERENCE_FPS;

        float sensitivity = EngineConfig.Camera.PLAY_LOOK_SENSITIVITY;
        camera.camYaw += (float) input.mouseDeltaX * sensitivity;
        camera.camPitch += (float) input.mouseDeltaY * sensitivity;
        camera.camPitch = Math.max((float) Math.toRadians(EngineConfig.Camera.PLAY_PITCH_MIN_DEG),
                Math.min((float) Math.toRadians(EngineConfig.Camera.PLAY_PITCH_MAX_DEG),
                        camera.camPitch));

        float speed = EngineConfig.Game.MOVE_SPEED;

        world.moveDir.set(0, 0, 0);

        if (input.isDown(Action.MOVE_FORWARD)) {
            world.moveDir.x += Math.sin(camera.camYaw);
            world.moveDir.z -= Math.cos(camera.camYaw);
        }
        if (input.isDown(Action.MOVE_BACKWARD)) {
            world.moveDir.x -= Math.sin(camera.camYaw);
            world.moveDir.z += Math.cos(camera.camYaw);
        }
        if (input.isDown(Action.MOVE_LEFT)) {
            world.moveDir.x -= Math.cos(camera.camYaw);
            world.moveDir.z -= Math.sin(camera.camYaw);
        }
        if (input.isDown(Action.MOVE_RIGHT)) {
            world.moveDir.x += Math.cos(camera.camYaw);
            world.moveDir.z += Math.sin(camera.camYaw);
        }

        if (world.moveDir.lengthSquared() > 0) {
            world.moveDir.normalize().mul(speed * frameScale);
            world.playerObject.pos.x += world.moveDir.x;
            collisionSystem.resolveHorizontalCollision(true, world.moveDir.x);
            world.playerObject.pos.z += world.moveDir.z;
            collisionSystem.resolveHorizontalCollision(false, world.moveDir.z);
            world.playerObject.rotation.y = (float) Math.atan2(world.moveDir.x, -world.moveDir.z);
        }

        // ★ 스케일 lerp: 프레임 독립
        float scaleLerp = frameIndependentLerp(EngineConfig.Game.SCALE_LERP, frameScale);
        physics.scaleX += (1.0f - physics.scaleX) * EngineConfig.Game.SCALE_LERP;
        physics.scaleY += (1.0f - physics.scaleY) * EngineConfig.Game.SCALE_LERP;
        physics.scaleZ += (1.0f - physics.scaleZ) * EngineConfig.Game.SCALE_LERP;

        physics.velocityY += EngineConfig.Physics.GRAVITY * frameScale;
        world.playerObject.pos.y += physics.velocityY;
        physics.isGrounded = false;

        if (world.playerObject.pos.y <= world.playerObject.size.y / 2.0f) {
            world.playerObject.pos.y = world.playerObject.size.y / 2.0f;
            physics.velocityY = 0;
            physics.isGrounded = true;
        }

        for (int i = 1; i < world.objects.size(); i++) {
            LevelObject obj = world.objects.get(i);
            if (obj instanceof BlockObject && collisionSystem
                    .checkAABBOverlap(world.playerObject, (BlockObject) obj)) {
                BlockObject b = (BlockObject) obj;
                if (physics.velocityY < 0) {
                    world.playerObject.pos.y = b.maxY() + world.playerObject.size.y / 2.0f;
                    physics.velocityY = 0;
                    physics.isGrounded = true;
                } else if (physics.velocityY > 0) {
                    world.playerObject.pos.y = b.minY() - world.playerObject.size.y / 2.0f;
                    physics.velocityY = 0;
                }
            }
        }

        if (input.isDown(Action.JUMP) && physics.isGrounded) {
            physics.velocityY = EngineConfig.Physics.JUMP_STRENGTH;
            physics.isGrounded = false;
            physics.scaleY = EngineConfig.Game.JUMP_SCALE_Y;
            physics.scaleX = EngineConfig.Game.JUMP_SCALE_XZ;
            physics.scaleZ = EngineConfig.Game.JUMP_SCALE_XZ;
        }
        boolean justLanded = !physics.wasGrounded && physics.isGrounded;
        if (justLanded) {
            physics.scaleY = EngineConfig.Game.LAND_SCALE_Y;
            physics.scaleX = EngineConfig.Game.LAND_SCALE_XZ;
            physics.scaleZ = EngineConfig.Game.LAND_SCALE_XZ;
        }
        physics.wasGrounded = physics.isGrounded;

        world.targetCam.set(
                world.playerObject.pos.x,
                world.playerObject.pos.y + EngineConfig.Camera.TARGET_Y_OFFSET,
                world.playerObject.pos.z);
        float camLerp = frameIndependentLerp(EngineConfig.Camera.SMOOTH_LERP, frameScale);
        camera.smoothCamPos.lerp(world.targetCam, camLerp);
    }

    /**
     * 프레임 독립 lerp 계수.
     *
     * <p>
     * 원래 {@code k}가 "60Hz 1프레임 기준" 계수일 때,
     * 임의의 dt에서 동일한 감쇠율을 얻기 위한 계수를 반환한다.
     *
     * <p>
     * 수식: {@code 1 - (1 - k)^(dt * refFps)}
     *
     * <p>
     * 검증: dt = 1/60, ref = 60 → frameScale = 1 → 결과 = k (기존과 동일)
     * dt = 1/144, ref = 60 → frameScale = 0.4167 → 결과 ≈ 0.4167 * k (근사)
     */
    private static float frameIndependentLerp(float k, float frameScale) {
        // k가 1에 가까우면 pow가 무의미하므로 그대로 반환
        if (k >= 1.0f)
            return 1.0f;
        if (k <= 0.0f)
            return 0.0f;
        return 1.0f - (float) Math.pow(1.0f - k, frameScale);
    }
}