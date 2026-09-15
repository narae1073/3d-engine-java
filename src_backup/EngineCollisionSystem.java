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

    

    

    
}
