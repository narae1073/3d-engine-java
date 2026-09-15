// 일반 블록 오브젝트

import org.joml.Vector3f;

// 3. 블록 오브젝트 (충돌 범위 계산 메서드 포함)
public class BlockObject extends LevelObject {
    public BlockObject(Vector3f pos, Vector3f size) {
        super(pos, size);
    }

    @Override
    public String getDisplayName() {
        return "Block";
    }

    public float minX() { return pos.x - size.x / 2.0f; }
    public float maxX() { return pos.x + size.x / 2.0f; }
    public float minY() { return pos.y - size.y / 2.0f; }
    public float maxY() { return pos.y + size.y / 2.0f; }
    public float minZ() { return pos.z - size.z / 2.0f; }
    public float maxZ() { return pos.z + size.z / 2.0f; }
}