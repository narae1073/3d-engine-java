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
            updateEditorMode();
        } else {
            updatePlayMode();
        }

        float targetTransition = camera.isOrthographic.get() ? 1.0f : 0.0f;
        camera.orthoTransition += (targetTransition - camera.orthoTransition)
                * EngineConfig.Camera.ORTHO_LERP;
    }

    public void updateEditorMode() {
        if (!camera.hasInitializedEditorCam) {
            camera.editorCamPos.set(
                    world.playerObject.pos.x + EngineConfig.Editor.INIT_OFFSET_X,
                    world.playerObject.pos.y + EngineConfig.Editor.INIT_OFFSET_Y,
                    world.playerObject.pos.z + EngineConfig.Editor.INIT_OFFSET_Z);
            camera.editorCamYaw = EngineConfig.Editor.INIT_YAW;
            camera.editorCamPitch = (float) Math.toRadians(EngineConfig.Editor.INIT_PITCH_DEG);
            camera.hasInitializedEditorCam = true;
        }

        boolean isAltPressed = (glfwGetKey(win.window, GLFW_KEY_LEFT_ALT) == GLFW_PRESS) ||
                (glfwGetKey(win.window, GLFW_KEY_RIGHT_ALT) == GLFW_PRESS);

        boolean isFlyActive = (glfwGetMouseButton(win.window, GLFW_MOUSE_BUTTON_MIDDLE) == GLFW_PRESS) ||
                (glfwGetMouseButton(win.window, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS && !isAltPressed);

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

        boolean isCtrlPressed = (glfwGetKey(win.window, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS) ||
                (glfwGetKey(win.window, GLFW_KEY_RIGHT_CONTROL) == GLFW_PRESS);

        float currentSpeed = camera.editorSpeed * (isCtrlPressed ? camera.editorSpeedMultiplier : 1.0f);
        Vector3f editorMoveDir = new Vector3f();

        if (isFlyActive) {
            if (input.w)
                editorMoveDir.add(forwardX, forwardY, forwardZ);
            if (input.s)
                editorMoveDir.sub(forwardX, forwardY, forwardZ);
            if (input.a)
                editorMoveDir.sub(rightX, 0, rightZ);

            if (input.d)
                editorMoveDir.add(rightX, 0, rightZ);
        } else {
            float flatForwardX = (float) Math.sin(camera.editorCamYaw);
            float flatForwardZ = (float) -Math.cos(camera.editorCamYaw);
            if (input.w)
                editorMoveDir.add(flatForwardX, 0, flatForwardZ);
            if (input.s)
                editorMoveDir.sub(flatForwardX, 0, flatForwardZ);
            if (input.a)
                editorMoveDir.sub(rightX, 0, rightZ);
            if (input.d)
                editorMoveDir.add(rightX, 0, rightZ);
            if (glfwGetKey(win.window, GLFW_KEY_E) == GLFW_PRESS)
                camera.editorCamPos.y += currentSpeed;
            if (glfwGetKey(win.window, GLFW_KEY_Q) == GLFW_PRESS)
                camera.editorCamPos.y -= currentSpeed;
        }

        if (editorMoveDir.lengthSquared() > 0) {
            editorMoveDir.normalize().mul(currentSpeed);
            camera.editorCamPos.add(editorMoveDir);
        }

        float wheel = imgui.ImGui.getIO().getMouseWheel();
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

    public void updatePlayMode() {
        float sensitivity = EngineConfig.Camera.PLAY_LOOK_SENSITIVITY;
        camera.camYaw += (float) input.mouseDeltaX * sensitivity;
        camera.camPitch += (float) input.mouseDeltaY * sensitivity;
        camera.camPitch = Math.max((float) Math.toRadians(EngineConfig.Camera.PLAY_PITCH_MIN_DEG),
                Math.min((float) Math.toRadians(EngineConfig.Camera.PLAY_PITCH_MAX_DEG),
                        camera.camPitch));
        input.clearMouseDelta();

        float speed = EngineConfig.Game.MOVE_SPEED;

        world.moveDir.set(0, 0, 0);

        if (input.w) {
            world.moveDir.x += Math.sin(camera.camYaw);
            world.moveDir.z -= Math.cos(camera.camYaw);
        }
        if (input.s) {
            world.moveDir.x -= Math.sin(camera.camYaw);
            world.moveDir.z += Math.cos(camera.camYaw);
        }
        if (input.a) {
            world.moveDir.x -= Math.cos(camera.camYaw);
            world.moveDir.z -= Math.sin(camera.camYaw);
        }
        if (input.d) {
            world.moveDir.x += Math.cos(camera.camYaw);
            world.moveDir.z += Math.sin(camera.camYaw);
        }

        if (world.moveDir.lengthSquared() > 0) {
            world.moveDir.normalize().mul(speed);
            world.playerObject.pos.x += world.moveDir.x;
            collisionSystem.resolveHorizontalCollision(true, world.moveDir.x);
            world.playerObject.pos.z += world.moveDir.z;
            collisionSystem.resolveHorizontalCollision(false, world.moveDir.z);
            world.playerObject.rotation.y = (float) Math.atan2(world.moveDir.x, -world.moveDir.z);
        }

        physics.scaleX += (1.0f - physics.scaleX) * EngineConfig.Game.SCALE_LERP;
        physics.scaleY += (1.0f - physics.scaleY) * EngineConfig.Game.SCALE_LERP;
        physics.scaleZ += (1.0f - physics.scaleZ) * EngineConfig.Game.SCALE_LERP;

        physics.velocityY += physics.gravity;
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

        if (input.space && physics.isGrounded) {
            physics.velocityY = physics.jumpStrength;
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
        camera.smoothCamPos.lerp(world.targetCam, EngineConfig.Camera.SMOOTH_LERP);
    }
}