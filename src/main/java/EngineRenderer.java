import imgui.ImGui;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_CLAMP_TO_BORDER;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL20.glAttachShader;
import static org.lwjgl.opengl.GL20.glCompileShader;
import static org.lwjgl.opengl.GL20.glCreateProgram;
import static org.lwjgl.opengl.GL20.glCreateShader;
import static org.lwjgl.opengl.GL20.glDeleteProgram;
import static org.lwjgl.opengl.GL20.glDeleteShader;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glLinkProgram;
import static org.lwjgl.opengl.GL20.glShaderSource;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glUniform3f;
import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL30.*;

public class EngineRenderer {
    private final Engine3DLWJGL engine;

    public EngineRenderer(Engine3DLWJGL engine) {
        this.engine = engine;
    }

    public void initShadowFBO() {
        engine.state.depthFBO = glGenFramebuffers();
        engine.state.depthMap = glGenTextures();

        glBindTexture(GL_TEXTURE_2D, engine.state.depthMap);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT, engine.state.SHADOW_WIDTH, engine.state.SHADOW_HEIGHT, 0,
                GL_DEPTH_COMPONENT, GL_FLOAT, (FloatBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        float[] borderColor = { 1.0f, 1.0f, 1.0f, 1.0f };
        glTexParameterfv(GL_TEXTURE_2D, GL_TEXTURE_BORDER_COLOR, borderColor);

        glBindFramebuffer(GL_FRAMEBUFFER, engine.state.depthFBO);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, engine.state.depthMap, 0);
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public void initShaders() {
        String depthVS = "#version 120\n" +
                "uniform mat4 lightSpaceMatrix;\n" +
                "uniform mat4 model;\n" +
                "void main() {\n" +
                "    gl_Position = lightSpaceMatrix * model * gl_Vertex;\n" +
                "}\n";
        String depthFS = "#version 120\n" +
                "void main() {\n" +
                "}\n";

        engine.state.depthShaderProgram = createProgram(depthVS, depthFS);
        // 유니폼 로케이션 초기화 시점에 한 번만 캐싱
        engine.state.locDepthLightSpace = glGetUniformLocation(engine.state.depthShaderProgram, "lightSpaceMatrix");
        engine.state.locDepthModel = glGetUniformLocation(engine.state.depthShaderProgram, "model");

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
                "    vec2 texelSize = vec2(1.0 / 2048.0);\n" +
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

        engine.state.mainShaderProgram = createProgram(mainVS, mainFS);

        engine.state.locModel = glGetUniformLocation(engine.state.mainShaderProgram, "model");
        engine.state.locView = glGetUniformLocation(engine.state.mainShaderProgram, "view");
        engine.state.locProj = glGetUniformLocation(engine.state.mainShaderProgram, "proj");
        engine.state.locLightSpaceMatrix = glGetUniformLocation(engine.state.mainShaderProgram, "lightSpaceMatrix");
        engine.state.locLightPos = glGetUniformLocation(engine.state.mainShaderProgram, "lightPos");
        engine.state.locLightAmbient = glGetUniformLocation(engine.state.mainShaderProgram, "lightAmbient");
        engine.state.locLightDiffuse = glGetUniformLocation(engine.state.mainShaderProgram, "lightDiffuse");
        engine.state.locObjectColor = glGetUniformLocation(engine.state.mainShaderProgram, "objectColor");
        engine.state.locUseLighting = glGetUniformLocation(engine.state.mainShaderProgram, "useLighting");
        engine.state.locShadowMap = glGetUniformLocation(engine.state.mainShaderProgram, "shadowMap");
    }

    public int createProgram(String vCode, String fCode) {
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

    public void render() {
        float nearPlane = 1.0f, farPlane = 40.0f;
        engine.state.lightProjMatrix.identity().setOrtho(-15.0f, 15.0f, -15.0f, 15.0f, nearPlane, farPlane);

        Vector3f lightTarget = new Vector3f(engine.state.playerObject.pos.x, 0.0f, engine.state.playerObject.pos.z);
        Vector3f actualLightPos = new Vector3f(engine.state.playerObject.pos.x + engine.state.lightPos.x,
                engine.state.lightPos.y, engine.state.playerObject.pos.z + engine.state.lightPos.z);
        engine.state.lightViewMatrix.identity().lookAt(actualLightPos, lightTarget, new Vector3f(0.0f, 1.0f, 0.0f));

        engine.state.lightProjMatrix.mul(engine.state.lightViewMatrix, engine.state.lightSpaceMatrix);

        Matrix4f shadowMatrix = new Matrix4f(engine.state.lightSpaceMatrix);
        org.joml.Vector4f shadowOrigin = new org.joml.Vector4f(0.0f, 0.0f, 0.0f, 1.0f).mul(shadowMatrix);
        shadowOrigin.mul(engine.state.SHADOW_WIDTH / 2.0f);

        float roundedX = Math.round(shadowOrigin.x);
        float roundedY = Math.round(shadowOrigin.y);
        float dx = (roundedX - shadowOrigin.x) * (2.0f / engine.state.SHADOW_WIDTH);
        float dy = (roundedY - shadowOrigin.y) * (2.0f / engine.state.SHADOW_HEIGHT);

        Matrix4f roundMatrix = new Matrix4f().translate(dx, dy, 0.0f);
        roundMatrix.mul(engine.state.lightSpaceMatrix, engine.state.lightSpaceMatrix);

        glViewport(0, 0, engine.state.SHADOW_WIDTH, engine.state.SHADOW_HEIGHT);
        glBindFramebuffer(GL_FRAMEBUFFER, engine.state.depthFBO);
        glClear(GL_DEPTH_BUFFER_BIT);

        glUseProgram(engine.state.depthShaderProgram);

        glUniformMatrix4fv(engine.state.locDepthLightSpace, false,
                engine.state.lightSpaceMatrix.get(engine.state.matrixBuffer));

        glEnable(GL_CULL_FACE);
        glCullFace(GL_FRONT);
        renderScene(engine.state.locDepthModel, false);
        glCullFace(GL_BACK);
        glDisable(GL_CULL_FACE);

        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        glViewport(0, 0, engine.state.width, engine.state.height);
        glClearColor(0.53f, 0.81f, 0.92f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glUseProgram(engine.state.mainShaderProgram);

        Matrix4f perspProj = new Matrix4f().setPerspective((float) Math.toRadians(60.0f),
                (float) engine.state.width / engine.state.height, 0.1f, 100.0f);
        float orthoSize = 6.0f;
        Matrix4f orthoProj = new Matrix4f().setOrtho(-orthoSize * ((float) engine.state.width / engine.state.height),
                orthoSize * ((float) engine.state.width / engine.state.height), -orthoSize, orthoSize, 0.1f, 100.0f);
        engine.state.projMatrix.set(perspProj).lerp(orthoProj, engine.state.orthoTransition);

        // 에딧 모드 플레이 모드 카메라 분리
        if (engine.state.isEditorMode.get()) {
            float fx = (float) (Math.sin(engine.state.editorCamYaw) * Math.cos(engine.state.editorCamPitch));
            float fy = (float) -Math.sin(engine.state.editorCamPitch);
            float fz = (float) (-Math.cos(engine.state.editorCamYaw) * Math.cos(engine.state.editorCamPitch));
            Vector3f eye = engine.state.editorCamPos;
            Vector3f center = new Vector3f(eye).add(fx, fy, fz);
            engine.state.viewMatrix.identity().lookAt(eye, center, new Vector3f(0, 1, 0));
        } else {
            engine.state.viewMatrix.identity()
                    .translate(0, 0, -engine.state.camDistance)
                    .rotateX(engine.state.camPitch)
                    .rotateY(engine.state.camYaw)
                    .translate(-engine.state.smoothCamPos.x, -engine.state.smoothCamPos.y,
                            -engine.state.smoothCamPos.z);
        }

        glUniformMatrix4fv(engine.state.locProj, false, engine.state.projMatrix.get(engine.state.matrixBuffer));
        glUniformMatrix4fv(engine.state.locView, false, engine.state.viewMatrix.get(engine.state.matrixBuffer));
        glUniformMatrix4fv(engine.state.locLightSpaceMatrix, false,
                engine.state.lightSpaceMatrix.get(engine.state.matrixBuffer));

        glUniform3f(engine.state.locLightPos, engine.state.lightPos.x, engine.state.lightPos.y,
                engine.state.lightPos.z);
        glUniform1f(engine.state.locLightAmbient, engine.state.lightAmbient);
        glUniform1f(engine.state.locLightDiffuse, engine.state.lightDiffuse);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, engine.state.depthMap);
        glUniform1i(engine.state.locShadowMap, 0);

        engine.state.editorInvVPMatrix.set(engine.state.projMatrix).mul(engine.state.viewMatrix).invert();

        renderScene(engine.state.locModel, true);

        // 에디터 모드에서 오브젝트 선택 시 하이라이트 및 기즈모 렌더링
        if (engine.state.selectedObjectIndex >= 0 && engine.state.selectedObjectIndex < engine.state.objects.size()) {
            glDisable(GL_DEPTH_TEST); // ← 추가: 기즈모/하이라이트가 오브젝트에 안 가려지게
            glLineWidth(4.0f);
            glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);

            LevelObject selObj = engine.state.objects.get(engine.state.selectedObjectIndex);
            Matrix4f model = new Matrix4f();
            model.translate(selObj.pos.x, selObj.pos.y, selObj.pos.z)
                    .rotateX(selObj.rotation.x)
                    .rotateY(selObj.rotation.y)
                    .rotateZ(selObj.rotation.z);

            if (selObj instanceof PlayerObject) {
                model.scale((selObj.size.x * engine.state.scaleX) * 1.02f,
                        (selObj.size.y * engine.state.scaleY) * 1.02f, (selObj.size.z * engine.state.scaleZ) * 1.02f);
            } else {
                model.scale(selObj.size.x * 1.02f, selObj.size.y * 1.02f, selObj.size.z * 1.02f);
            }

            // 하이라이트 박스 (노란색)
            glUniform1i(engine.state.locUseLighting, 0);
            glUniform3f(engine.state.locObjectColor, 1.0f, 1.0f, 0.0f);
            glUniformMatrix4fv(engine.state.locModel, false, model.get(engine.state.matrixBuffer));
            if (selObj instanceof PlayerObject) {
                drawSphereGeometry();
            } else {
                drawCubeGeometry();
            }

            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
            glLineWidth(1.0f);

            // ==========================================
            // [핵심 수정] 기즈모 모드 렌더링 매트릭스 설정
            // ==========================================
            Matrix4f gizmoBase = new Matrix4f().translate(selObj.pos.x, selObj.pos.y, selObj.pos.z);

            // 로컬 기즈모 모드일 경우, 오브젝트의 회전값을 기즈모 베이스에 적용!
            if (engine.state.isLocalGizmo) {
                gizmoBase.rotateX(selObj.rotation.x)
                        .rotateY(selObj.rotation.y)
                        .rotateZ(selObj.rotation.z);
            }

            if (engine.state.gizmoMode == 0) {
                // --- 1. POSITION 기즈모 (직선 형태) ---
                glLineWidth(5.0f);

                // X축
                glUniform3f(engine.state.locObjectColor, engine.state.activeGizmoAxis == 1 ? 1.0f : 1.0f,
                        engine.state.activeGizmoAxis == 1 ? 1.0f : 0.0f, 0.0f);
                glUniformMatrix4fv(engine.state.locModel, false, gizmoBase.get(engine.state.matrixBuffer));
                glBegin(GL_LINES);
                glVertex3f(0, 0, 0);
                glVertex3f(2, 0, 0);
                glEnd();

                // Y축
                glUniform3f(engine.state.locObjectColor, engine.state.activeGizmoAxis == 2 ? 1.0f : 0.0f,
                        engine.state.activeGizmoAxis == 2 ? 1.0f : 1.0f, 0.0f);
                glUniformMatrix4fv(engine.state.locModel, false, gizmoBase.get(engine.state.matrixBuffer));
                glBegin(GL_LINES);
                glVertex3f(0, 0, 0);
                glVertex3f(0, 2, 0);
                glEnd();

                // Z축
                glUniform3f(engine.state.locObjectColor, engine.state.activeGizmoAxis == 3 ? 1.0f : 0.0f,
                        engine.state.activeGizmoAxis == 3 ? 1.0f : 0.0f, 1.0f);
                glUniformMatrix4fv(engine.state.locModel, false, gizmoBase.get(engine.state.matrixBuffer));
                glBegin(GL_LINES);
                glVertex3f(0, 0, 0);
                glVertex3f(0, 0, 2);
                glEnd();

                glLineWidth(1.0f);

            } else if (engine.state.gizmoMode == 1) {
                // --- 2. SCALE 기즈모 (선 끝에 작은 큐브가 달린 형태) ---
                glLineWidth(3.0f);

                // X축 선 및 큐브 핸들
                glUniform3f(engine.state.locObjectColor, engine.state.activeGizmoAxis == 1 ? 1.0f : 1.0f,
                        engine.state.activeGizmoAxis == 1 ? 1.0f : 0.0f, 0.0f);
                glUniformMatrix4fv(engine.state.locModel, false, gizmoBase.get(engine.state.matrixBuffer));
                glBegin(GL_LINES);
                glVertex3f(0, 0, 0);
                glVertex3f(2, 0, 0);
                glEnd();

                Matrix4f cubeX = new Matrix4f(gizmoBase).translate(2, 0, 0).scale(0.3f);
                glUniformMatrix4fv(engine.state.locModel, false, cubeX.get(engine.state.matrixBuffer));
                drawCubeGeometry();

                // Y축 선 및 큐브 핸들
                glUniform3f(engine.state.locObjectColor, engine.state.activeGizmoAxis == 2 ? 1.0f : 0.0f,
                        engine.state.activeGizmoAxis == 2 ? 1.0f : 1.0f, 0.0f);
                glUniformMatrix4fv(engine.state.locModel, false, gizmoBase.get(engine.state.matrixBuffer));
                glBegin(GL_LINES);
                glVertex3f(0, 0, 0);
                glVertex3f(0, 2, 0);
                glEnd();

                Matrix4f cubeY = new Matrix4f(gizmoBase).translate(0, 2, 0).scale(0.3f);
                glUniformMatrix4fv(engine.state.locModel, false, cubeY.get(engine.state.matrixBuffer));
                drawCubeGeometry();

                // Z축 선 및 큐브 핸들
                glUniform3f(engine.state.locObjectColor, engine.state.activeGizmoAxis == 3 ? 1.0f : 0.0f,
                        engine.state.activeGizmoAxis == 3 ? 1.0f : 0.0f, 1.0f);
                glUniformMatrix4fv(engine.state.locModel, false, gizmoBase.get(engine.state.matrixBuffer));
                glBegin(GL_LINES);
                glVertex3f(0, 0, 0);
                glVertex3f(0, 0, 2);
                glEnd();

                Matrix4f cubeZ = new Matrix4f(gizmoBase).translate(0, 0, 2).scale(0.3f);
                glUniformMatrix4fv(engine.state.locModel, false, cubeZ.get(engine.state.matrixBuffer));
                drawCubeGeometry();

                glLineWidth(1.0f);

            } else if (engine.state.gizmoMode == 2) {
                // --- 3. ROTATION 기즈모 (둥근 원형 링 형태) ---
                glLineWidth(3.0f);
                float radius = 2.0f;
                int segments = 32;

                // X축 회전 링 (YZ 평면)
                glUniform3f(engine.state.locObjectColor, engine.state.activeGizmoAxis == 1 ? 1.0f : 1.0f,
                        engine.state.activeGizmoAxis == 1 ? 1.0f : 0.0f, 0.0f);
                glUniformMatrix4fv(engine.state.locModel, false, gizmoBase.get(engine.state.matrixBuffer));
                glBegin(GL_LINE_LOOP);
                for (int i = 0; i < segments; i++) {
                    float angle = (float) (2.0f * Math.PI * i / segments);
                    float y = radius * (float) Math.cos(angle);
                    float z = radius * (float) Math.sin(angle);
                    glVertex3f(0.0f, y, z);
                }
                glEnd();

                // Y축 회전 링 (XZ 평면)
                glUniform3f(engine.state.locObjectColor, engine.state.activeGizmoAxis == 2 ? 1.0f : 0.0f,
                        engine.state.activeGizmoAxis == 2 ? 1.0f : 1.0f, 0.0f);
                glUniformMatrix4fv(engine.state.locModel, false, gizmoBase.get(engine.state.matrixBuffer));
                glBegin(GL_LINE_LOOP);
                for (int i = 0; i < segments; i++) {
                    float angle = (float) (2.0f * Math.PI * i / segments);
                    float x = radius * (float) Math.cos(angle);
                    float z = radius * (float) Math.sin(angle);
                    glVertex3f(x, 0.0f, z);
                }
                glEnd();

                // Z축 회전 링 (XY 평면)
                glUniform3f(engine.state.locObjectColor, engine.state.activeGizmoAxis == 3 ? 1.0f : 0.0f,
                        engine.state.activeGizmoAxis == 3 ? 1.0f : 0.0f, 1.0f);
                glUniformMatrix4fv(engine.state.locModel, false, gizmoBase.get(engine.state.matrixBuffer));
                glBegin(GL_LINE_LOOP);
                for (int i = 0; i < segments; i++) {
                    float angle = (float) (2.0f * Math.PI * i / segments);
                    float x = radius * (float) Math.cos(angle);
                    float y = radius * (float) Math.sin(angle);
                    glVertex3f(x, y, 0.0f);
                }
                glEnd();

                glLineWidth(1.0f);
            }

            glUniform1i(engine.state.locUseLighting, 1);

            glEnable(GL_DEPTH_TEST); // ← 추가: 다음 프레임 일반 렌더링을 위해 복구
        }

        glUseProgram(0);
    }

    public void renderScene(int modelLoc, boolean isMainPass) {
        engine.state.modelMatrix.identity();
        glUniformMatrix4fv(modelLoc, false, engine.state.modelMatrix.get(engine.state.matrixBuffer));

        if (isMainPass) {
            glUniform3f(engine.state.locObjectColor, 0.5f, 0.5f, 0.5f);
            glUniform1i(engine.state.locUseLighting, 1);
        }
        drawGroundGeometry();

        if (isMainPass) {
            glUniform3f(engine.state.locObjectColor, 0.0f, 0.0f, 0.0f);
            glUniform1i(engine.state.locUseLighting, 0);
            drawGridGeometry();
            glUniform1i(engine.state.locUseLighting, 1);
        }

        for (LevelObject obj : engine.state.objects) {
            engine.state.modelMatrix.identity()
                    .translate(obj.pos.x, obj.pos.y, obj.pos.z)
                    .rotateX(obj.rotation.x)
                    .rotateY(obj.rotation.y)
                    .rotateZ(obj.rotation.z);

            if (obj instanceof PlayerObject) {
                engine.state.modelMatrix.scale(obj.size.x * engine.state.scaleX, obj.size.y * engine.state.scaleY,
                        obj.size.z * engine.state.scaleZ);
                if (isMainPass) {
                    if (engine.state.isGrounded)
                        glUniform3f(engine.state.locObjectColor, 0.2f, 0.9f, 1.0f);
                    else
                        glUniform3f(engine.state.locObjectColor, 0.1f, 0.6f, 1.0f);
                }
            } else if (obj instanceof BlockObject) {
                engine.state.modelMatrix.scale(obj.size.x, obj.size.y, obj.size.z);
                if (isMainPass) {
                    glUniform3f(engine.state.locObjectColor, 0.95f, 0.5f, 0.2f);
                }
            }

            glUniformMatrix4fv(modelLoc, false, engine.state.modelMatrix.get(engine.state.matrixBuffer));
            if (obj instanceof PlayerObject) {
                drawSphereGeometry();
            } else {
                drawCubeGeometry();
            }
        }
    }

    public void drawCubeGeometry() {
        glBegin(GL_QUADS);
        for (int i = 0; i < engine.state.faces.length; i++) {
            glNormal3f(engine.state.normals[i][0], engine.state.normals[i][1], engine.state.normals[i][2]);
            for (int vertIndex : engine.state.faces[i]) {
                glVertex3f(engine.state.vertices[vertIndex][0], engine.state.vertices[vertIndex][1],
                        engine.state.vertices[vertIndex][2]);
            }
        }
        glEnd();
    }

    public void drawSphereGeometry() {
        int stacks = 20;
        int slices = 20;
        float radius = 0.5f; // 큐브(-0.5~0.5)랑 크기 맞춤

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

    public void drawGroundGeometry() {
        glBegin(GL_QUADS);
        glNormal3f(0, 1, 0);
        glVertex3f(-30.0f, 0.0f, -30.0f);
        glVertex3f(30.0f, 0.0f, -30.0f);
        glVertex3f(30.0f, 0.0f, 30.0f);
        glVertex3f(-30.0f, 0.0f, 30.0f);
        glEnd();
    }

    public void drawGridGeometry() {
        glBegin(GL_LINES);
        for (int i = -30; i <= 30; i++) {
            glVertex3f(-30, 0.001f, i);
            glVertex3f(30, 0.001f, i);
            glVertex3f(i, 0.001f, -30);
            glVertex3f(i, 0.001f, 30);
        }
        glEnd();
    }

    public void cleanup() {
        glDeleteFramebuffers(engine.state.depthFBO);
        glDeleteTextures(engine.state.depthMap);
        glDeleteProgram(engine.state.mainShaderProgram);
        glDeleteProgram(engine.state.depthShaderProgram);
    }
}