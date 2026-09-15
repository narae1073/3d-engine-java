public class EngineCollisionSystem {
    private final WorldState world;

    public EngineCollisionSystem(WorldState world) {
        this.world = world;
    }

    public void resolveHorizontalCollision(boolean isX, float moveDir) {
        for (int i = 1; i < world.objects.size(); i++) {
            LevelObject obj = world.objects.get(i);
            if (obj instanceof BlockObject && checkAABBOverlap(world.playerObject, (BlockObject) obj)) {
                BlockObject b = (BlockObject) obj;
                if (isX)
                    world.playerObject.pos.x = moveDir > 0
                            ? b.minX() - world.playerObject.size.x / 2.0f
                            : b.maxX() + world.playerObject.size.x / 2.0f;
                else
                    world.playerObject.pos.z = moveDir > 0
                            ? b.minZ() - world.playerObject.size.z / 2.0f
                            : b.maxZ() + world.playerObject.size.z / 2.0f;
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