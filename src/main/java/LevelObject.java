import org.joml.Vector3f;

public abstract class LevelObject {
    public Vector3f pos;
    public Vector3f size;
    public Vector3f rotation;

    public LevelObject(Vector3f pos, Vector3f size) {
        this.pos = new Vector3f(pos);
        this.size = new Vector3f(size);
        this.rotation = new Vector3f(0.0f, 0.0f, 0.0f);
    }

    // 오브젝트 종류에 따라 다르게 처리할 수 있는 메서드 (필요시 구현)
    public abstract String getDisplayName();
}