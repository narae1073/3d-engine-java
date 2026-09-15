import org.joml.Vector3f;

public class LightState {
    public final Vector3f lightPos = new Vector3f(
            EngineConfig.Light.POS_X,
            EngineConfig.Light.POS_Y,
            EngineConfig.Light.POS_Z);
    public float lightAmbient = EngineConfig.Light.AMBIENT;
    public float lightDiffuse = EngineConfig.Light.DIFFUSE;
}