import imgui.ImGui;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_CLAMP_TO_BORDER;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * 렌더링 전용 시스템.
 *
 * 이전에 EngineState에 흩어져 있던 매트릭스/버퍼/셰이더 로케이션/FBO 핸들은
 * 렌더링 파이프라인 내부 구현 세부사항이므로 전부 이 클래스의 private 필드로 흡수했다.
 * 외부 시스템은 카메라/조명/물리/에디터/월드 컴포넌트만 참조한다.
 *
 * 단 하나의 예외: 에디터 기즈모 피킹을 위해 EditorGizmoSystem이 필요로 하는
 * 역 VP 행렬(editorInvVPMatrix)은 CameraState에 기록한다. (렌더 패스에서 계산해
 * 카메라 컴포넌트에 되돌려 놓는 단방향 흐름)
 */
public class EngineRenderer implements EngineSystem {

    // ============================================================
    // 주입된 컴포넌트 (읽기 전용 참조)
    // ============================================================
    private final WindowContext win;
    private final CameraState camera;
    private final PhysicsState physics;
    private final EditorState editor;
    private final WorldState world;

    // ============================================================
    // 렌더 전용 상태 (이전 EngineState에서 흡수)
    // ============================================================
    // 셰도우 맵
    private final int SHADOW_WIDTH = EngineConfig.Render.SHADOW_WIDTH;
    private final int SHADOW_HEIGHT = EngineConfig.Render.SHADOW_HEIGHT;
    private int depthFBO;
    private int depthMap;

    // 셰이더 프로그램
    private int mainShaderProgram;
    private int depthShaderProgram;

    // main shader uniform locations
    private int locModel, locView, locProj, locLightSpaceMatrix;
    private int locLightPos, locLightAmbient, locLightDiffuse, locObjectColor, locUseLighting, locShadowMap;

    // depth pass uniform locations
    private int locDepthLightSpace, locDepthModel;

    // 매트릭스/버퍼 (프레임 간 재사용)
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);
    private final Matrix4f projMatrix = new Matrix4f();
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f lightProjMatrix = new Matrix4f();
    private final Matrix4f lightViewMatrix = new Matrix4f();
    private final Matrix4f lightSpaceMatrix = new Matrix4f();
    private final Matrix4f modelMatrix = new Matrix4f();

    // ============================================================
    // 지오메트리 (큐브는 정적 상수로 승격)
    // ============================================================
    private static final float[][] CUBE_VERTICES = {
            { -0.5f, -0.5f, -0.5f }, { 0.5f, -0.5f, -0.5f }, { 0.5f, 0.5f, -0.5f }, { -0.5f, 0.5f, -0.5f },
            { -0.5f, -0.5f, 0.5f }, { 0.5f, -0.5f, 0.5f }, { 0.5f, 0.5f, 0.5f }, { -0.5f, 0.5f, 0.5f }
    };
    private static final int[][] CUBE_FACES = {
            { 4, 5, 6, 7 }, { 1, 0, 3, 2 }, { 3, 2, 6, 7 },
            { 4, 0, 1, 5 }, { 5, 1, 2, 6 }, { 0, 4, 7, 3 }
    };
    private static final float[][] CUBE_NORMALS = {
            { 0, 0, 1 }, { 0, 0, -1 }, { 0, 1, 0 }, { 0, -1, 0 }, { 1, 0, 0 }, { -1, 0, 0 }
    };

    public EngineRenderer(WindowContext win, CameraState camera,
            PhysicsState physics, EditorState editor, WorldState world) {
        this.win = win;
        this.camera = camera;
        this.physics = physics;
        this.editor = editor;
        this.world = world;
    }

    // ============================================================
    // 생명주기
    // ============================================================
    @Override
    public void init() {
        initShadowFBO();
        initShaders();
    }

    @Override
    public void dispose() {
        glDeleteFramebuffers(depthFBO);
        glDeleteTextures(depthMap);
        glDeleteProgram(mainShaderProgram);
        glDeleteProgram(depthShaderProgram);
    }

    // ============================================================
    // 셰도우 FBO
    // ============================================================
    private void initShadowFBO() {
        depthFBO = glGenFramebuffers();
        depthMap = glGenTextures();

        glBindTexture(GL_TEXTURE_2D, depthMap);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT, SHADOW_WIDTH, SHADOW_HEIGHT, 0,
                GL_DEPTH_COMPONENT, GL_FLOAT, (FloatBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        float[] borderColor = { 1.0f, 1.0f, 1.0f, 1.0f };
        glTexParameterfv(GL_TEXTURE_2D, GL_TEXTURE_BORDER_COLOR, borderColor);

        glBindFramebuffer(GL_FRAMEBUFFER, depthFBO);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthMap, 0);
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    // ============================================================
    // 셰이더 컴파일
    // ============================================================
    private void initShaders() {
        String depthVS = "#version 120\n" +
                "uniform mat4 lightSpaceMatrix;\n" +
                "uniform mat4 model;\n" +
                "void main() {\n" +
                "    gl_Position = lightSpaceMatrix * model * gl_Vertex;\n" +
                "}\n";
        String depthFS = "#version 120\n" +
                "void main() {\n" +
                "}\n";

        depthShaderProgram = createProgram(depthVS, depthFS);
        locDepthLightSpace = glGetUniformLocation(depthShaderProgram, "lightSpaceMatrix");
        locDepthModel = glGetUniformLocation(depthShaderProgram, "model");

        String mainVS = "#version 120\n" +
                "uniform mat4 model;\n" +
                "uniform mat4 view;\n" +
                "uniform mat4 proj;\n" +
                "uniform mat4 lightSpaceMatrix;\n" +
                "varying vec3 FragPos;\n" +
                "varying vec3 Normal;\n" +
                "varying vec4 FragPosLightSpace;\n" +
                "void main() {\n" +
                "    FragPos = vec3(model * gl_Vertex);\n" +
                "    Normal = mat3(model) * gl_Normal;\n" +
                "    FragPosLightSpace = lightSpaceMatrix * vec4(FragPos, 1.0);\n" +
                "    gl_Position = proj * view * vec4(FragPos, 1.0);\n" +
                "}\n";

        String mainFS = "#version 120\n" +
                "varying vec3 FragPos;\n" +
                "varying vec3 Normal;\n" +
                "varying vec4 FragPosLightSpace;\n" +
                "uniform vec3 lightPos;\n" +
                "uniform float lightAmbient;\n" +
                "uniform float lightDiffuse;\n" +
                "uniform vec3 objectColor;\n" +
                "uniform bool useLighting;\n" +
                "uniform sampler2D shadowMap;\n" +
                "float ShadowCalculation(vec4 fragPosLightSpace, vec3 normal, vec3 lightDir) {\n" +
                "    vec3 projCoords = fragPosLightSpace.xyz / fragPosLightSpace.w;\n" +
                "    projCoords = projCoords * 0.5 + 0.5;\n" +
                "    if(projCoords.z > 1.0) return 0.0;\n" +
                "    float bias = max(0.001 * (1.0 - dot(normal, lightDir)), 0.0005);\n" +
                "    float shadow = 0.0;\n" +
                // ↓ shadow width를 프로퍼티에서 주입
                "    vec2 texelSize = vec2(1.0 / " + EngineConfig.Render.SHADOW_WIDTH + ".0);\n" +
                "    for(int x = -1; x <= 1; ++x) {\n" +
                "        for(int y = -1; y <= 1; ++y) {\n" +
                "            float pcfDepth = texture2D(shadowMap, projCoords.xy + vec2(x, y) * texelSize).r;\n" +
                "            shadow += projCoords.z - bias > pcfDepth ? 1.0 : 0.0;\n" +
                "        }\n" +
                "    }\n" +
                "    shadow /= 9.0;\n" +
                "    return shadow;\n" +
                "}\n" +
                "void main() {\n" +
                "    if (!useLighting) {\n" +
                "        gl_FragColor = vec4(objectColor, 1.0);\n" +
                "        return;\n" +
                "    }\n" +
                "    vec3 norm = normalize(Normal);\n" +
                "    vec3 lightDir = normalize(lightPos - FragPos);\n" +
                "    float diff = max(dot(norm, lightDir), 0.0);\n" +
                "    vec3 ambient = lightAmbient * objectColor;\n" +
                "    vec3 diffuse = diff * lightDiffuse * objectColor;\n" +
                "    float shadow = ShadowCalculation(FragPosLightSpace, norm, lightDir);\n" +
                "    vec3 result = ambient + (1.0 - shadow) * diffuse;\n" +
                "    gl_FragColor = vec4(result, 1.0);\n" +
                "}\n";

        mainShaderProgram = createProgram(mainVS, mainFS);

        locModel = glGetUniformLocation(mainShaderProgram, "model");
        locView = glGetUniformLocation(mainShaderProgram, "view");
        locProj = glGetUniformLocation(mainShaderProgram, "proj");
        locLightSpaceMatrix = glGetUniformLocation(mainShaderProgram, "lightSpaceMatrix");
        locLightPos = glGetUniformLocation(mainShaderProgram, "lightPos");
        locLightAmbient = glGetUniformLocation(mainShaderProgram, "lightAmbient");
        locLightDiffuse = glGetUniformLocation(mainShaderProgram, "lightDiffuse");
        locObjectColor = glGetUniformLocation(mainShaderProgram, "objectColor");
        locUseLighting = glGetUniformLocation(mainShaderProgram, "useLighting");
        locShadowMap = glGetUniformLocation(mainShaderProgram, "shadowMap");
    }

    private int createProgram(String vCode, String fCode) {
        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, vCode);
        glCompileShader(vs);

        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, fCode);
        glCompileShader(fs);

        int prog = glCreateProgram();
        glAttachShader(prog, vs);
        glAttachShader(prog, fs);
        glLinkProgram(prog);

        glDeleteShader(vs);
        glDeleteShader(fs);
        return prog;
    }

    // ============================================================
    // 메인 렌더 패스
    // ============================================================
    @Override
    public void render() {
        // ---------- 1. 라이트 공간 행렬 계산 ----------
        lightProjMatrix.identity().setOrtho(
                -EngineConfig.Render.LIGHT_ORTHO_HALF, EngineConfig.Render.LIGHT_ORTHO_HALF,
                -EngineConfig.Render.LIGHT_ORTHO_HALF, EngineConfig.Render.LIGHT_ORTHO_HALF,
                EngineConfig.Render.LIGHT_NEAR, EngineConfig.Render.LIGHT_FAR);

        Vector3f lightTarget = new Vector3f(world.playerObject.pos.x, 0.0f, world.playerObject.pos.z);
        Vector3f actualLightPos = new Vector3f(
                world.playerObject.pos.x + EngineConfig.Light.POS_X,
                EngineConfig.Light.POS_Y,
                world.playerObject.pos.z + EngineConfig.Light.POS_Z);
        lightViewMatrix.identity().lookAt(actualLightPos, lightTarget, new Vector3f(0.0f, 1.0f, 0.0f));
        lightProjMatrix.mul(lightViewMatrix, lightSpaceMatrix);

        // ---------- 2. 그림자 떨림 방지 (라운딩) ----------
        Matrix4f shadowMatrix = new Matrix4f(lightSpaceMatrix);
        Vector4f shadowOrigin = new Vector4f(0.0f, 0.0f, 0.0f, 1.0f).mul(shadowMatrix);
        shadowOrigin.mul(SHADOW_WIDTH / 2.0f);

        float roundedX = Math.round(shadowOrigin.x);
        float roundedY = Math.round(shadowOrigin.y);
        float dx = (roundedX - shadowOrigin.x) * (2.0f / SHADOW_WIDTH);
        float dy = (roundedY - shadowOrigin.y) * (2.0f / SHADOW_HEIGHT);

        Matrix4f roundMatrix = new Matrix4f().translate(dx, dy, 0.0f);
        roundMatrix.mul(lightSpaceMatrix, lightSpaceMatrix);

        // ---------- 3. 뎁스 패스 (그림자 맵) ----------
        glViewport(0, 0, SHADOW_WIDTH, SHADOW_HEIGHT);
        glBindFramebuffer(GL_FRAMEBUFFER, depthFBO);
        glClear(GL_DEPTH_BUFFER_BIT);

        glUseProgram(depthShaderProgram);
        glUniformMatrix4fv(locDepthLightSpace, false, lightSpaceMatrix.get(matrixBuffer));

        glEnable(GL_CULL_FACE);
        glCullFace(GL_FRONT);
        renderScene(locDepthModel, false);
        glCullFace(GL_BACK);
        glDisable(GL_CULL_FACE);

        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        // ---------- 4. 메인 패스 ----------
        glViewport(0, 0, win.width, win.height);
        float[] clear = EngineConfig.Render.clearRGBA();
        glClearColor(clear[0], clear[1], clear[2], clear[3]);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glUseProgram(mainShaderProgram);

        // 투영 행렬 (원근 ↔ 직교 lerp)
        Matrix4f perspProj = new Matrix4f().setPerspective(
                (float) Math.toRadians(EngineConfig.Render.CAM_FOV_DEG),
                (float) win.width / win.height,
                EngineConfig.Render.CAM_NEAR,
                EngineConfig.Render.CAM_FAR);

        float orthoSize = EngineConfig.Render.ORTHO_HALF_SIZE;
        Matrix4f orthoProj = new Matrix4f().setOrtho(
                -orthoSize * ((float) win.width / win.height),
                orthoSize * ((float) win.width / win.height),
                -orthoSize, orthoSize,
                EngineConfig.Render.CAM_NEAR, EngineConfig.Render.CAM_FAR);
        projMatrix.set(perspProj).lerp(orthoProj, camera.orthoTransition);

        // 뷰 행렬 (에디터 ↔ 플레이 분기)
        if (camera.isEditorMode.get()) {
            float fx = (float) (Math.sin(camera.editorCamYaw) * Math.cos(camera.editorCamPitch));
            float fy = (float) -Math.sin(camera.editorCamPitch);
            float fz = (float) (-Math.cos(camera.editorCamYaw) * Math.cos(camera.editorCamPitch));
            Vector3f eye = camera.editorCamPos;
            Vector3f center = new Vector3f(eye).add(fx, fy, fz);
            viewMatrix.identity().lookAt(eye, center, new Vector3f(0, 1, 0));
        } else {
            viewMatrix.identity()
                    .translate(0, 0, -EngineConfig.Camera.CAM_DISTANCE) // ★
                    .rotateX(camera.camPitch)
                    .rotateY(camera.camYaw)
                    .translate(-camera.smoothCamPos.x, -camera.smoothCamPos.y, -camera.smoothCamPos.z);
        }

        glUniformMatrix4fv(locProj, false, projMatrix.get(matrixBuffer));
        glUniformMatrix4fv(locView, false, viewMatrix.get(matrixBuffer));
        glUniformMatrix4fv(locLightSpaceMatrix, false, lightSpaceMatrix.get(matrixBuffer));

        // uniform 설정
        glUniform3f(locLightPos, EngineConfig.Light.POS_X,
                EngineConfig.Light.POS_Y, EngineConfig.Light.POS_Z);
        glUniform1f(locLightAmbient, EngineConfig.Light.AMBIENT);
        glUniform1f(locLightDiffuse, EngineConfig.Light.DIFFUSE);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, depthMap);
        glUniform1i(locShadowMap, 0);

        // 기즈모 피킹용 역 VP 행렬을 카메라 컴포넌트에 기록
        // (EditorGizmoSystem이 다음 프레임 update에서 사용)
        camera.editorInvVPMatrix.set(projMatrix).mul(viewMatrix).invert();

        // ---------- 5. 씬 렌더 ----------
        renderScene(locModel, true);

        // ---------- 6. 선택 오브젝트 하이라이트 + 기즈모 ----------
        renderEditorOverlay();

        glUseProgram(0);
    }

    // ============================================================
    // 씬 렌더 (뎁스 패스 / 메인 패스 공용)
    // ============================================================
    private void renderScene(int modelLoc, boolean isMainPass) {
        modelMatrix.identity();
        glUniformMatrix4fv(modelLoc, false, modelMatrix.get(matrixBuffer));

        if (isMainPass) {
            float[] c = EngineConfig.Render.COLOR_GROUND;
            glUniform3f(locObjectColor, c[0], c[1], c[2]);
            glUniform1i(locUseLighting, 1);
        }
        drawGroundGeometry();

        if (isMainPass) {
            float[] c = EngineConfig.Render.COLOR_GRID;
            glUniform3f(locObjectColor, c[0], c[1], c[2]);
            glUniform1i(locUseLighting, 0);
            drawGridGeometry();
            glUniform1i(locUseLighting, 1);
        }

        for (LevelObject obj : world.objects) {
            modelMatrix.identity()
                    .translate(obj.pos.x, obj.pos.y, obj.pos.z)
                    .rotateX(obj.rotation.x)
                    .rotateY(obj.rotation.y)
                    .rotateZ(obj.rotation.z);

            if (obj instanceof PlayerObject) {
                modelMatrix.scale(obj.size.x * physics.scaleX,
                        obj.size.y * physics.scaleY,
                        obj.size.z * physics.scaleZ);
                if (isMainPass) {
                    float[] c = physics.isGrounded
                            ? EngineConfig.Render.COLOR_PLAYER_GROUND
                            : EngineConfig.Render.COLOR_PLAYER_AIR;
                    glUniform3f(locObjectColor, c[0], c[1], c[2]);
                }
            } else if (obj instanceof BlockObject) {
                modelMatrix.scale(obj.size.x, obj.size.y, obj.size.z);
                if (isMainPass) {
                    float[] c = EngineConfig.Render.COLOR_BLOCK;
                    glUniform3f(locObjectColor, c[0], c[1], c[2]);
                }
            }

            glUniformMatrix4fv(modelLoc, false, modelMatrix.get(matrixBuffer));
            if (obj instanceof PlayerObject) {
                drawSphereGeometry();
            } else {
                drawCubeGeometry();
            }
        }
    }

    // ============================================================
    // 에디터 오버레이 (하이라이트 + 기즈모)
    // ============================================================
    private void renderEditorOverlay() {
        if (editor.selectedObjectIndex < 0 || editor.selectedObjectIndex >= world.objects.size()) {
            return;
        }

        glDisable(GL_DEPTH_TEST); // 기즈모/하이라이트가 오브젝트에 가려지지 않도록
        glLineWidth(EngineConfig.Render.LINE_WIDTH_HIGHLIGHT);
        glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);

        LevelObject selObj = world.objects.get(editor.selectedObjectIndex);

        // ---------- 하이라이트 박스 ----------
        Matrix4f highlight = new Matrix4f();
        highlight.translate(selObj.pos.x, selObj.pos.y, selObj.pos.z)
                .rotateX(selObj.rotation.x)
                .rotateY(selObj.rotation.y)
                .rotateZ(selObj.rotation.z);

        if (selObj instanceof PlayerObject) {
            highlight.scale(
                    (selObj.size.x * physics.scaleX) * EngineConfig.Render.HIGHLIGHT_SCALE,
                    (selObj.size.y * physics.scaleY) * EngineConfig.Render.HIGHLIGHT_SCALE,
                    (selObj.size.z * physics.scaleZ) * EngineConfig.Render.HIGHLIGHT_SCALE);
        } else {
            highlight.scale(selObj.size.x * EngineConfig.Render.HIGHLIGHT_SCALE,
                    selObj.size.y * EngineConfig.Render.HIGHLIGHT_SCALE,
                    selObj.size.z * EngineConfig.Render.HIGHLIGHT_SCALE);
        }

        glUniform1i(locUseLighting, 0);
        float[] c = EngineConfig.Render.COLOR_HIGHLIGHT;
        glUniform3f(locObjectColor, c[0], c[1], c[2]);

        glUniformMatrix4fv(locModel, false, highlight.get(matrixBuffer));
        if (selObj instanceof PlayerObject) {
            drawSphereGeometry();
        } else {
            drawCubeGeometry();
        }

        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        glLineWidth(EngineConfig.Render.LINE_WIDTH_DEFAULT);

        // ---------- 기즈모 베이스 행렬 ----------
        Matrix4f gizmoBase = new Matrix4f().translate(selObj.pos.x, selObj.pos.y, selObj.pos.z);
        if (editor.isLocalGizmo) {
            gizmoBase.rotateX(selObj.rotation.x)
                    .rotateY(selObj.rotation.y)
                    .rotateZ(selObj.rotation.z);
        }

        if (editor.gizmoMode == 0) {
            renderPositionGizmo(gizmoBase);
        } else if (editor.gizmoMode == 1) {
            renderScaleGizmo(gizmoBase);
        } else if (editor.gizmoMode == 2) {
            renderRotationGizmo(gizmoBase);
        }

        glUniform1i(locUseLighting, 1);
        glEnable(GL_DEPTH_TEST);
    }

    // ---------- 위치 기즈모 (직선 3축) ----------
    private void renderPositionGizmo(Matrix4f base) {
        glLineWidth(EngineConfig.Render.LINE_WIDTH_POSITION);

        // X축
        glUniform3f(locObjectColor,
                editor.activeGizmoAxis == 1 ? 1.0f : 1.0f,
                editor.activeGizmoAxis == 1 ? 1.0f : 0.0f,
                0.0f);
        glUniformMatrix4fv(locModel, false, base.get(matrixBuffer));
        glBegin(GL_LINES);
        glVertex3f(0, 0, 0);
        glVertex3f(EngineConfig.Gizmo.AXIS_LENGTH, 0, 0);
        glEnd();

        // Y축
        glUniform3f(locObjectColor,
                editor.activeGizmoAxis == 2 ? 1.0f : 0.0f,
                editor.activeGizmoAxis == 2 ? 1.0f : 1.0f,
                0.0f);
        glUniformMatrix4fv(locModel, false, base.get(matrixBuffer));
        glBegin(GL_LINES);
        glVertex3f(0, 0, 0);
        glVertex3f(0, EngineConfig.Gizmo.AXIS_LENGTH, 0);
        glEnd();

        // Z축
        glUniform3f(locObjectColor,
                editor.activeGizmoAxis == 3 ? 1.0f : 0.0f,
                editor.activeGizmoAxis == 3 ? 1.0f : 0.0f,
                1.0f);
        glUniformMatrix4fv(locModel, false, base.get(matrixBuffer));
        glBegin(GL_LINES);
        glVertex3f(0, 0, 0);
        glVertex3f(0, 0, EngineConfig.Gizmo.AXIS_LENGTH);
        glEnd();

        glLineWidth(EngineConfig.Render.LINE_WIDTH_DEFAULT);
    }

    // ---------- 스케일 기즈모 (선 + 큐브 핸들) ----------
    private void renderScaleGizmo(Matrix4f base) {
        glLineWidth(EngineConfig.Render.LINE_WIDTH_SCALE_ROT);

        // X축 + 큐브
        glUniform3f(locObjectColor,
                editor.activeGizmoAxis == 1 ? 1.0f : 1.0f,
                editor.activeGizmoAxis == 1 ? 1.0f : 0.0f,
                0.0f);
        glUniformMatrix4fv(locModel, false, base.get(matrixBuffer));
        glBegin(GL_LINES);
        glVertex3f(0, 0, 0);
        glVertex3f(EngineConfig.Gizmo.AXIS_LENGTH, 0, 0);
        glEnd();
        Matrix4f cubeX = new Matrix4f(base)
                .translate(EngineConfig.Gizmo.AXIS_LENGTH, 0, 0)
                .scale(EngineConfig.Gizmo.HANDLE_CUBE_SIZE);
        glUniformMatrix4fv(locModel, false, cubeX.get(matrixBuffer));
        drawCubeGeometry();

        // Y축 + 큐브
        glUniform3f(locObjectColor,
                editor.activeGizmoAxis == 2 ? 1.0f : 0.0f,
                editor.activeGizmoAxis == 2 ? 1.0f : 1.0f,
                0.0f);
        glUniformMatrix4fv(locModel, false, base.get(matrixBuffer));
        glBegin(GL_LINES);
        glVertex3f(0, 0, 0);
        glVertex3f(0, EngineConfig.Gizmo.AXIS_LENGTH, 0);
        glEnd();
        Matrix4f cubeY = new Matrix4f(base).translate(0, EngineConfig.Gizmo.AXIS_LENGTH, 0)
                .scale(EngineConfig.Gizmo.HANDLE_CUBE_SIZE);
        glUniformMatrix4fv(locModel, false, cubeY.get(matrixBuffer));
        drawCubeGeometry();

        // Z축 + 큐브
        glUniform3f(locObjectColor,
                editor.activeGizmoAxis == 3 ? 1.0f : 0.0f,
                editor.activeGizmoAxis == 3 ? 1.0f : 0.0f,
                1.0f);
        glUniformMatrix4fv(locModel, false, base.get(matrixBuffer));
        glBegin(GL_LINES);
        glVertex3f(0, 0, 0);
        glVertex3f(0, 0, EngineConfig.Gizmo.AXIS_LENGTH);
        glEnd();
        Matrix4f cubeZ = new Matrix4f(base).translate(0, 0, EngineConfig.Gizmo.AXIS_LENGTH)
                .scale(EngineConfig.Gizmo.HANDLE_CUBE_SIZE);
        glUniformMatrix4fv(locModel, false, cubeZ.get(matrixBuffer));
        drawCubeGeometry();

        glLineWidth(EngineConfig.Render.LINE_WIDTH_DEFAULT);
    }

    // ---------- 회전 기즈모 (3개의 링) ----------
    private void renderRotationGizmo(Matrix4f base) {
        glLineWidth(EngineConfig.Render.LINE_WIDTH_SCALE_ROT);
        float radius = EngineConfig.Gizmo.RING_RADIUS;
        int segments = EngineConfig.Gizmo.RING_SEGMENTS;

        // X축 회전 링 (YZ 평면)
        glUniform3f(locObjectColor,
                editor.activeGizmoAxis == 1 ? 1.0f : 1.0f,
                editor.activeGizmoAxis == 1 ? 1.0f : 0.0f,
                0.0f);
        glUniformMatrix4fv(locModel, false, base.get(matrixBuffer));
        glBegin(GL_LINE_LOOP);
        for (int i = 0; i < segments; i++) {
            float angle = (float) (2.0f * Math.PI * i / segments);
            glVertex3f(0.0f, radius * (float) Math.cos(angle), radius * (float) Math.sin(angle));
        }
        glEnd();

        // Y축 회전 링 (XZ 평면)
        glUniform3f(locObjectColor,
                editor.activeGizmoAxis == 2 ? 1.0f : 0.0f,
                editor.activeGizmoAxis == 2 ? 1.0f : 1.0f,
                0.0f);
        glUniformMatrix4fv(locModel, false, base.get(matrixBuffer));
        glBegin(GL_LINE_LOOP);
        for (int i = 0; i < segments; i++) {
            float angle = (float) (2.0f * Math.PI * i / segments);
            glVertex3f(radius * (float) Math.cos(angle), 0.0f, radius * (float) Math.sin(angle));
        }
        glEnd();

        // Z축 회전 링 (XY 평면)
        glUniform3f(locObjectColor,
                editor.activeGizmoAxis == 3 ? 1.0f : 0.0f,
                editor.activeGizmoAxis == 3 ? 1.0f : 0.0f,
                1.0f);
        glUniformMatrix4fv(locModel, false, base.get(matrixBuffer));
        glBegin(GL_LINE_LOOP);
        for (int i = 0; i < segments; i++) {
            float angle = (float) (2.0f * Math.PI * i / segments);
            glVertex3f(radius * (float) Math.cos(angle), radius * (float) Math.sin(angle), 0.0f);
        }
        glEnd();

        glLineWidth(1.0f);
    }

    // ============================================================
    // 지오메트리
    // ============================================================
    private void drawCubeGeometry() {
        glBegin(GL_QUADS);
        for (int i = 0; i < CUBE_FACES.length; i++) {
            glNormal3f(CUBE_NORMALS[i][0], CUBE_NORMALS[i][1], CUBE_NORMALS[i][2]);
            for (int vertIndex : CUBE_FACES[i]) {
                glVertex3f(CUBE_VERTICES[vertIndex][0],
                        CUBE_VERTICES[vertIndex][1],
                        CUBE_VERTICES[vertIndex][2]);
            }
        }
        glEnd();
    }

    private void drawSphereGeometry() {
        int stacks = EngineConfig.Render.SPHERE_STACKS;
        int slices = EngineConfig.Render.SPHERE_SLICES;
        float radius = EngineConfig.Render.SPHERE_RADIUS;

        for (int i = 0; i < stacks; i++) {
            float lat0 = (float) Math.PI * (-0.5f + (float) i / stacks);
            float lat1 = (float) Math.PI * (-0.5f + (float) (i + 1) / stacks);
            float y0 = (float) Math.sin(lat0), r0 = (float) Math.cos(lat0);
            float y1 = (float) Math.sin(lat1), r1 = (float) Math.cos(lat1);

            glBegin(GL_QUAD_STRIP);
            for (int j = 0; j <= slices; j++) {
                float lng = 2.0f * (float) Math.PI * (float) j / slices;
                float cx = (float) Math.cos(lng);
                float cz = (float) Math.sin(lng);

                float nx0 = cx * r0, nz0 = cz * r0;
                glNormal3f(nx0, y0, nz0);
                glVertex3f(radius * nx0, radius * y0, radius * nz0);

                float nx1 = cx * r1, nz1 = cz * r1;
                glNormal3f(nx1, y1, nz1);
                glVertex3f(radius * nx1, radius * y1, radius * nz1);
            }
            glEnd();
        }
    }

    private void drawGroundGeometry() {
        float h = EngineConfig.Render.GROUND_HALF;
        glBegin(GL_QUADS);
        glNormal3f(0, 1, 0);
        glVertex3f(-h, 0.0f, -h);
        glVertex3f(h, 0.0f, -h);
        glVertex3f(h, 0.0f, h);
        glVertex3f(-h, 0.0f, h);
        glEnd();
    }

    private void drawGridGeometry() {
        int n = EngineConfig.Render.GRID_HALF_COUNT;
        float h = EngineConfig.Render.GROUND_HALF;
        float y = EngineConfig.Render.GRID_LINE_Y;
        glBegin(GL_LINES);
        for (int i = -n; i <= n; i++) {
            glVertex3f(-h, y, i);
            glVertex3f(h, y, i);
            glVertex3f(i, y, -h);
            glVertex3f(i, y, h);
        }
        glEnd();
    }

}