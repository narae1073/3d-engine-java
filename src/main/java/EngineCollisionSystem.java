import imgui.ImGui;
import org.joml.Vector3f;
import org.joml.Vector4f;

import static org.lwjgl.glfw.GLFW.*;

public class EngineCollisionSystem {
    private final Engine3DLWJGL engine;

    public EngineCollisionSystem(Engine3DLWJGL engine) {
        this.engine = engine;
    }

    public void resolveHorizontalCollision(boolean isX, float moveDir) {
        for (int i = 1; i < engine.state.objects.size(); i++) {
            LevelObject obj = engine.state.objects.get(i);
            if (obj instanceof BlockObject && checkAABBOverlap(engine.state.playerObject, (BlockObject) obj)) {
                BlockObject b = (BlockObject) obj;
                if (isX)
                    engine.state.playerObject.pos.x = moveDir > 0 ? b.minX() - engine.state.playerObject.size.x / 2.0f : b.maxX() + engine.state.playerObject.size.x / 2.0f;
                else
                    engine.state.playerObject.pos.z = moveDir > 0 ? b.minZ() - engine.state.playerObject.size.z / 2.0f : b.maxZ() + engine.state.playerObject.size.z / 2.0f;
            }
        }
    }

    public boolean checkAABBOverlap(PlayerObject p, BlockObject b) {
        float pHalfX = p.size.x / 2.0f;
        float pHalfY = p.size.y / 2.0f;
        float pHalfZ = p.size.z / 2.0f;

        return (p.pos.x + pHalfX > b.minX() && p.pos.x - pHalfX < b.maxX()) &&
                (p.pos.y + pHalfY > b.minY() && p.pos.y - pHalfY < b.maxY()) &&
                (p.pos.z + pHalfZ > b.minZ() && p.pos.z - pHalfZ < b.maxZ());
    }

    public void handleEditorObjectPicking() {
        if (!engine.state.isEditorMode.get() || ImGui.getIO().getWantCaptureMouse()) {
            engine.state.prevMousePressed = false;
            return;
        }

        boolean currentPressed = (glfwGetMouseButton(engine.state.window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS);
        if (currentPressed && !engine.state.prevMousePressed) {
            castRayAndSelectObject();
        }

        engine.state.prevMousePressed = currentPressed;
    }

    public void castRayAndSelectObject() {
        if (!engine.state.isEditorMode.get() || ImGui.getIO().getWantCaptureMouse()) {
            return;
        }

        double[] mouseX = new double[1];
        double[] mouseY = new double[1];
        glfwGetCursorPos(engine.state.window, mouseX, mouseY);

        int[] winWidth = new int[1];
        int[] winHeight = new int[1];
        glfwGetWindowSize(engine.state.window, winWidth, winHeight);

        float x = (float) (2.0 * mouseX[0] / winWidth[0] - 1.0);
        float y = (float) (1.0 - 2.0 * mouseY[0] / winHeight[0]);

        Vector4f rayStart = new Vector4f(x, y, -1.0f, 1.0f).mul(engine.state.editorInvVPMatrix);
        rayStart.div(rayStart.w);

        Vector4f rayEnd = new Vector4f(x, y, 1.0f, 1.0f).mul(engine.state.editorInvVPMatrix);
        rayEnd.div(rayEnd.w);

        Vector3f rayOrigin = new Vector3f(rayStart.x, rayStart.y, rayStart.z);
        Vector3f rayDir = new Vector3f(rayEnd.x - rayStart.x, rayEnd.y - rayStart.y, rayEnd.z - rayStart.z).normalize();

        int hitTarget = -1;
        float closestDist = Float.MAX_VALUE;

        for (int i = 0; i < engine.state.objects.size(); i++) {
            LevelObject obj = engine.state.objects.get(i);
            float minX = obj.pos.x - obj.size.x / 2.0f;
            float maxX = obj.pos.x + obj.size.x / 2.0f;
            float minY = obj.pos.y - obj.size.y / 2.0f;
            float maxY = obj.pos.y + obj.size.y / 2.0f;
            float minZ = obj.pos.z - obj.size.z / 2.0f;
            float maxZ = obj.pos.z + obj.size.z / 2.0f;

            float dist = getRayBoxIntersectionDistance(rayOrigin, rayDir, minX, maxX, minY, maxY, minZ, maxZ);
            if (dist >= 0 && dist < closestDist) {
                closestDist = dist;
                hitTarget = i;
            }
        }

        engine.state.selectedObjectIndex = hitTarget;
    }

    public float getRayBoxIntersectionDistance(Vector3f origin, Vector3f dir,
                                                float minX, float maxX,
                                                float minY, float maxY,
                                                float minZ, float maxZ) {
        float t1 = (minX - origin.x) / dir.x;
        float t2 = (maxX - origin.x) / dir.x;
        float t3 = (minY - origin.y) / dir.y;
        float t4 = (maxY - origin.y) / dir.y;
        float t5 = (minZ - origin.z) / dir.z;
        float t6 = (maxZ - origin.z) / dir.z;

        float tmin = Math.max(Math.max(Math.min(t1, t2), Math.min(t3, t4)), Math.min(t5, t6));
        float tmax = Math.min(Math.min(Math.max(t1, t2), Math.max(t3, t4)), Math.max(t5, t6));

        if (tmax < 0 || tmin > tmax)
            return -1.0f;
        return (tmin < 0) ? tmax : tmin;
    }
}
