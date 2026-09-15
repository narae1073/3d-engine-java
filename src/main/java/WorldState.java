import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

// 씬 오브젝트 및 재사용 벡터
public class WorldState {
    public final List<LevelObject> objects = new ArrayList<>();
    public PlayerObject playerObject;

    // 매 프레임 재사용 (GC 방지)
    public final Vector3f moveDir = new Vector3f();
    public final Vector3f targetCam = new Vector3f();
}