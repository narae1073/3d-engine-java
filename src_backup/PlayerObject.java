// 플레이어 오브젝트 (조종 가능한 특별한 오브젝트)

import org.joml.Vector3f;

// 2. 플레이어 오브젝트
public class PlayerObject extends LevelObject {
    public PlayerObject(Vector3f pos, Vector3f size) {
        super(pos, size);
    }

    @Override
    public String getDisplayName() {
        return "Player";
    }
}