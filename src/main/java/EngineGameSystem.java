import org.joml.Vector3f;

public class EngineGameSystem {
    private final Engine3DLWJGL engine;

    public EngineGameSystem(Engine3DLWJGL engine) {
        this.engine = engine;
    }

    public void initWorld() {
        engine.state.playerObject = new PlayerObject(new Vector3f(0.0f, 0.5f, 0.0f), new Vector3f(1.0f, 1.0f, 1.0f));
        engine.state.objects.add(engine.state.playerObject);

        engine.state.objects.add(new BlockObject(new Vector3f(3, 0, 0), new Vector3f(1, 1, 1)));
        engine.state.objects.add(new BlockObject(new Vector3f(0, 3, 0), new Vector3f(1, 1, 1)));
        engine.state.objects.add(new BlockObject(new Vector3f(0, 0, 3), new Vector3f(1, 1, 1)));

        engine.state.smoothCamPos.set(engine.state.playerObject.pos.x, engine.state.playerObject.pos.y + 0.3f,
                engine.state.playerObject.pos.z);
    }

    public void update() {
        if (engine.state.isEditorMode.get()) {
            updateEditorMode();
        } else {
            updatePlayMode();
        }

        float targetTransition = engine.state.isOrthographic.get() ? 1.0f : 0.0f;
        engine.state.orthoTransition += (targetTransition - engine.state.orthoTransition) * 0.12f;
    }

    public void updateEditorMode() {
        // 에디터 모드 카메라 초기 위치
        if (!engine.state.hasInitializedEditorCam) {
            engine.state.editorCamPos.set(
                    engine.state.playerObject.pos.x + 3.0f,
                    engine.state.playerObject.pos.y + 3.0f,
                    engine.state.playerObject.pos.z + 3.0f);
            engine.state.editorCamYaw = -0.75f;
            engine.state.editorCamPitch = (float) Math.toRadians(30);
            engine.state.hasInitializedEditorCam = true;
        }

        boolean isAltPressed = (org.lwjgl.glfw.GLFW.glfwGetKey(engine.state.window,
                org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS) ||
                (org.lwjgl.glfw.GLFW.glfwGetKey(engine.state.window,
                        org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS);

        boolean isFlyActive = (org.lwjgl.glfw.GLFW.glfwGetMouseButton(engine.state.window,
                org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == org.lwjgl.glfw.GLFW.GLFW_PRESS) ||
                (org.lwjgl.glfw.GLFW.glfwGetMouseButton(engine.state.window,
                        org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                        && !isAltPressed);

        if (isFlyActive) {
            float sensitivity = 0.005f;
            engine.state.editorCamYaw += imgui.ImGui.getIO().getMouseDeltaX() * sensitivity;
            engine.state.editorCamPitch += imgui.ImGui.getIO().getMouseDeltaY() * sensitivity;
            engine.state.editorCamPitch = Math.max((float) Math.toRadians(-85),
                    Math.min((float) Math.toRadians(85), engine.state.editorCamPitch));
        }

        float forwardX = (float) (Math.sin(engine.state.editorCamYaw) * Math.cos(engine.state.editorCamPitch));
        float forwardY = (float) -Math.sin(engine.state.editorCamPitch);
        float forwardZ = (float) (-Math.cos(engine.state.editorCamYaw) * Math.cos(engine.state.editorCamPitch));

        float rightX = (float) Math.cos(engine.state.editorCamYaw);
        float rightZ = (float) Math.sin(engine.state.editorCamYaw);

        boolean isCtrlPressed = (org.lwjgl.glfw.GLFW.glfwGetKey(engine.state.window,
                org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS) ||
                (org.lwjgl.glfw.GLFW.glfwGetKey(engine.state.window,
                        org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS);

        float currentSpeed = engine.state.editorSpeed * (isCtrlPressed ? engine.state.editorSpeedMultiplier : 1.0f);
        Vector3f editorMoveDir = new Vector3f();

        if (isFlyActive) {
            if (engine.state.w)
                editorMoveDir.add(forwardX, forwardY, forwardZ);
            if (engine.state.s)
                editorMoveDir.sub(forwardX, forwardY, forwardZ);
            if (engine.state.a)
                editorMoveDir.sub(rightX, 0, rightZ);
            if (engine.state.d)
                editorMoveDir.add(rightX, 0, rightZ);
        } else {
            float flatForwardX = (float) Math.sin(engine.state.editorCamYaw);
            float flatForwardZ = (float) -Math.cos(engine.state.editorCamYaw);

            if (engine.state.w)
                editorMoveDir.add(flatForwardX, 0, flatForwardZ);
            if (engine.state.s)
                editorMoveDir.sub(flatForwardX, 0, flatForwardZ);
            if (engine.state.a)
                editorMoveDir.sub(rightX, 0, rightZ);
            if (engine.state.d)
                editorMoveDir.add(rightX, 0, rightZ);

            if (org.lwjgl.glfw.GLFW.glfwGetKey(engine.state.window,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_E) == org.lwjgl.glfw.GLFW.GLFW_PRESS)
                engine.state.editorCamPos.y += currentSpeed;
            if (org.lwjgl.glfw.GLFW.glfwGetKey(engine.state.window,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_Q) == org.lwjgl.glfw.GLFW.GLFW_PRESS)
                engine.state.editorCamPos.y -= currentSpeed;
        }

        if (editorMoveDir.lengthSquared() > 0) {
            editorMoveDir.normalize().mul(currentSpeed);
            engine.state.editorCamPos.add(editorMoveDir);
        }

        float wheel = imgui.ImGui.getIO().getMouseWheel();
        if (wheel != 0) {
            float zoomSpeed = 0.6f;
            engine.state.editorCamPos.add(forwardX * wheel * zoomSpeed, forwardY * wheel * zoomSpeed,
                    forwardZ * wheel * zoomSpeed);
        }

        engine.state.smoothCamPos.set(engine.state.editorCamPos);
        engine.state.camYaw = engine.state.editorCamYaw;
        engine.state.camPitch = engine.state.editorCamPitch;
    }

    public void updatePlayMode() {
        float sensitivity = 0.0025f;
        engine.state.camYaw += (float) engine.state.mouseDeltaX * sensitivity;
        engine.state.camPitch += (float) engine.state.mouseDeltaY * sensitivity;
        engine.state.camPitch = Math.max((float) Math.toRadians(-10),
                Math.min((float) Math.toRadians(85), engine.state.camPitch));

        engine.state.mouseDeltaX = 0;
        engine.state.mouseDeltaY = 0;

        float speed = 0.08f;
        engine.state.moveDir.set(0, 0, 0);

        if (engine.state.w) {
            engine.state.moveDir.x += Math.sin(engine.state.camYaw);
            engine.state.moveDir.z -= Math.cos(engine.state.camYaw);
        }
        if (engine.state.s) {
            engine.state.moveDir.x -= Math.sin(engine.state.camYaw);
            engine.state.moveDir.z += Math.cos(engine.state.camYaw);
        }
        if (engine.state.a) {
            engine.state.moveDir.x -= Math.cos(engine.state.camYaw);
            engine.state.moveDir.z -= Math.sin(engine.state.camYaw);
        }
        if (engine.state.d) {
            engine.state.moveDir.x += Math.cos(engine.state.camYaw);
            engine.state.moveDir.z += Math.sin(engine.state.camYaw);
        }

        if (engine.state.moveDir.lengthSquared() > 0) {
            engine.state.moveDir.normalize().mul(speed);
            engine.state.playerObject.pos.x += engine.state.moveDir.x;
            new EngineCollisionSystem(engine).resolveHorizontalCollision(true, engine.state.moveDir.x);
            engine.state.playerObject.pos.z += engine.state.moveDir.z;
            new EngineCollisionSystem(engine).resolveHorizontalCollision(false, engine.state.moveDir.z);
            engine.state.playerObject.rotation.y = (float) Math.atan2(engine.state.moveDir.x, -engine.state.moveDir.z);
        }

        engine.state.scaleX += (1.0f - engine.state.scaleX) * 0.15f;
        engine.state.scaleY += (1.0f - engine.state.scaleY) * 0.15f;
        engine.state.scaleZ += (1.0f - engine.state.scaleZ) * 0.15f;

        engine.state.velocityY += engine.state.gravity;
        engine.state.playerObject.pos.y += engine.state.velocityY;
        engine.state.isGrounded = false;

        if (engine.state.playerObject.pos.y <= engine.state.playerObject.size.y / 2.0f) {
            engine.state.playerObject.pos.y = engine.state.playerObject.size.y / 2.0f;
            engine.state.velocityY = 0;
            engine.state.isGrounded = true;
        }

        for (int i = 1; i < engine.state.objects.size(); i++) {
            LevelObject obj = engine.state.objects.get(i);
            if (obj instanceof BlockObject && new EngineCollisionSystem(engine)
                    .checkAABBOverlap(engine.state.playerObject, (BlockObject) obj)) {
                BlockObject b = (BlockObject) obj;
                if (engine.state.velocityY < 0) {
                    engine.state.playerObject.pos.y = b.maxY() + engine.state.playerObject.size.y / 2.0f;
                    engine.state.velocityY = 0;
                    engine.state.isGrounded = true;
                } else if (engine.state.velocityY > 0) {
                    engine.state.playerObject.pos.y = b.minY() - engine.state.playerObject.size.y / 2.0f;
                    engine.state.velocityY = 0;
                }
            }
        }

        if (engine.state.space && engine.state.isGrounded) {
            engine.state.velocityY = engine.state.jumpStrength;
            engine.state.isGrounded = false;
            engine.state.scaleY = 1.4f;
            engine.state.scaleX = 0.7f;
            engine.state.scaleZ = 0.7f;
        }

        boolean justLanded = !engine.state.wasGrounded && engine.state.isGrounded;
        if (justLanded) {
            engine.state.scaleY = 0.6f;
            engine.state.scaleX = 1.3f;
            engine.state.scaleZ = 1.3f;
        }

        engine.state.wasGrounded = engine.state.isGrounded;

        engine.state.targetCam.set(engine.state.playerObject.pos.x, engine.state.playerObject.pos.y + 0.3f,
                engine.state.playerObject.pos.z);
        engine.state.smoothCamPos.lerp(engine.state.targetCam, 0.15f);
    }
}
