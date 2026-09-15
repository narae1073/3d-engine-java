import imgui.ImGui;
import org.joml.Vector3f;
import org.joml.Vector4f;

import static org.lwjgl.glfw.GLFW.*;

public class EditorGizmoSystem {
    private final Engine3DLWJGL engine;

    // 드래그 중인 축 (0: 없음, 1: X축, 2: Y축, 3: Z축)
    private int activeAxis = 0;
    private final Vector3f dragStartPos = new Vector3f();

    // 드래그할 때 마우스 이전 위치를 기억할 변수
    private double lastMouseX = 0;
    private double lastMouseY = 0;

    // 오비트 회전용 누적 각도 및 거리 변수
    private float orbitYaw = 0.0f;
    private float orbitPitch = 0.0f;
    private float orbitDistance = 5.0f;

    private boolean prevRightMousePressed = false;
    private boolean fKeyPressed = false;

    // 클래스 상단 변수
    private boolean lKeyPressed = false; // 키 중복 입력 방지용

    public EditorGizmoSystem(Engine3DLWJGL engine) {
        this.engine = engine;
    }

    public void update() {
        if (!engine.state.isEditorMode.get() || ImGui.getIO().getWantCaptureMouse()) {
            engine.state.prevMousePressed = false;
            engine.state.activeGizmoAxis = 0;
            return;
        }

        // 숫자키 1, 2, 3으로 기즈모 모드 변경
        if (glfwGetKey(engine.state.window, GLFW_KEY_1) == GLFW_PRESS) {
            engine.state.gizmoMode = 0;
        } else if (glfwGetKey(engine.state.window, GLFW_KEY_2) == GLFW_PRESS) {
            engine.state.gizmoMode = 1;
        } else if (glfwGetKey(engine.state.window, GLFW_KEY_3) == GLFW_PRESS) {
            engine.state.gizmoMode = 2;
        }

        // F키를 누르면 선택된 오브젝트로 카메라 포커스 (한 번만 트리거)
        if (glfwGetKey(engine.state.window, GLFW_KEY_F) == GLFW_PRESS) {
            if (!fKeyPressed) {
                fKeyPressed = true;
                if (engine.state.selectedObjectIndex >= 0
                        && engine.state.selectedObjectIndex < engine.state.objects.size()) {
                    LevelObject selObj = engine.state.objects.get(engine.state.selectedObjectIndex);

                    // 오브젝트 크기에 비례해서 적당한 거리 확보 (너무 붙거나 멀어지지 않게)
                    float focusDistance = Math.max(3.0f, selObj.size.length() * 2.0f);

                    // 현재 보고 있는 방향(yaw/pitch)은 그대로 유지
                    float fx = (float) (Math.sin(engine.state.editorCamYaw) * Math.cos(engine.state.editorCamPitch));
                    float fy = (float) -Math.sin(engine.state.editorCamPitch);
                    float fz = (float) (-Math.cos(engine.state.editorCamYaw) * Math.cos(engine.state.editorCamPitch));

                    // 그 방향의 반대쪽으로 focusDistance만큼 물러난 지점에 카메라 배치
                    engine.state.editorCamPos.set(
                            selObj.pos.x - fx * focusDistance,
                            selObj.pos.y - fy * focusDistance,
                            selObj.pos.z - fz * focusDistance);
                }
            }
        } else {
            fKeyPressed = false;
        }

        // update() 내부 (Local / Global 토글)
        if (glfwGetKey(engine.state.window, GLFW_KEY_L) == GLFW_PRESS) {
            if (!lKeyPressed) {
                engine.state.isLocalGizmo = !engine.state.isLocalGizmo;
                lKeyPressed = true;
                System.out.println("기즈모 모드: " + (engine.state.isLocalGizmo ? "Local" : "Global"));
            }
        } else {
            lKeyPressed = false;
        }

        // 우클릭 드래그로 시점 변경 (Alt + 우클릭 = 선택 오브젝트 중심 오빗)
        // 우클릭 드래그로 시점 변경 (Alt + 우클릭 = 선택 오브젝트 중심 오빗)
        boolean isOrbitAltPressed = (glfwGetKey(engine.state.window, GLFW_KEY_LEFT_ALT) == GLFW_PRESS) ||
                (glfwGetKey(engine.state.window, GLFW_KEY_RIGHT_ALT) == GLFW_PRESS);
        boolean isRightMousePressed = isOrbitAltPressed &&
                (glfwGetMouseButton(engine.state.window, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS);

        if (isRightMousePressed) {
            if (engine.state.selectedObjectIndex >= 0
                    && engine.state.selectedObjectIndex < engine.state.objects.size()) {
                LevelObject selObj = engine.state.objects.get(engine.state.selectedObjectIndex);

                double[] mouseX = new double[1];
                double[] mouseY = new double[1];
                glfwGetCursorPos(engine.state.window, mouseX, mouseY);

                if (!prevRightMousePressed) {
                    lastMouseX = mouseX[0];
                    lastMouseY = mouseY[0];
                    // 드래그 시작할 때 딱 한 번만 현재 거리 기억 (매 프레임 재계산 X → 떨림 방지)
                    orbitDistance = new Vector3f(engine.state.editorCamPos).sub(selObj.pos).length();
                    if (orbitDistance < 1.0f)
                        orbitDistance = 5.0f;
                }

                float deltaX = (float) (mouseX[0] - lastMouseX);
                float deltaY = (float) (mouseY[0] - lastMouseY);

                float rotSpeed = 0.005f; // 자유비행 룩어라운드랑 감도 맞춤
                engine.state.editorCamYaw += deltaX * rotSpeed;
                engine.state.editorCamPitch -= deltaY * rotSpeed;
                engine.state.editorCamPitch = Math.max(-1.5f, Math.min(1.5f, engine.state.editorCamPitch));

                // 오빗 중 휠로 줌 인/아웃 (거리에 비례해서 자연스럽게)
                float wheel = ImGui.getIO().getMouseWheel();
                if (wheel != 0) {
                    orbitDistance -= wheel * (orbitDistance * 0.15f);
                    orbitDistance = Math.max(0.5f, Math.min(50.0f, orbitDistance));
                }

                // 새 lookAt 카메라의 forward 벡터와 부호를 맞춰서 계산 (eye = target - forward * dist)
                float fx = (float) (Math.sin(engine.state.editorCamYaw) * Math.cos(engine.state.editorCamPitch));
                float fy = (float) -Math.sin(engine.state.editorCamPitch);
                float fz = (float) (-Math.cos(engine.state.editorCamYaw) * Math.cos(engine.state.editorCamPitch));

                engine.state.editorCamPos.set(
                        selObj.pos.x - fx * orbitDistance,
                        selObj.pos.y - fy * orbitDistance,
                        selObj.pos.z - fz * orbitDistance);

                lastMouseX = mouseX[0];
                lastMouseY = mouseY[0];
                prevRightMousePressed = true;
                return;
            }
        } else {
            prevRightMousePressed = false;
        }

        boolean currentPressed = (glfwGetMouseButton(engine.state.window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS);

        // 1. 마우스를 막 클릭한 순간 (클릭 시작)
        if (currentPressed && !engine.state.prevMousePressed) {
            checkGizmoClick();

            if (activeAxis == 0) {
                castRayAndSelectObject();
            } else {
                double[] mouseX = new double[1];
                double[] mouseY = new double[1];
                glfwGetCursorPos(engine.state.window, mouseX, mouseY);
                lastMouseX = mouseX[0];
                lastMouseY = mouseY[0];
            }
        }

        // 2. 클릭을 유지한 채 드래그 중인 순간
        else if (currentPressed && engine.state.prevMousePressed && activeAxis != 0) {
            double[] mouseX = new double[1];
            double[] mouseY = new double[1];
            glfwGetCursorPos(engine.state.window, mouseX, mouseY);

            float deltaX = (float) (mouseX[0] - lastMouseX);
            float deltaY = (float) (mouseY[0] - lastMouseY);

            LevelObject selObj = engine.state.objects.get(engine.state.selectedObjectIndex);
            float sensitivity = 0.02f;

            // 1. 카메라 시점 정보 가져오기 (화면 상의 X, Z 방향 파악용)
            float camYaw = engine.state.editorCamYaw;
            float camRightX = (float) Math.cos(camYaw);
            float camRightZ = (float) Math.sin(camYaw);
            float camForwardX = (float) Math.sin(camYaw);
            float camForwardZ = (float) -Math.cos(camYaw);

            // 2. 현재 선택된 축의 3D 방향 벡터 구하기 (로컬/월드 반영)
            org.joml.Vector3f axisDir = new org.joml.Vector3f();
            if (activeAxis == 1)
                axisDir.set(1, 0, 0);
            else if (activeAxis == 2)
                axisDir.set(0, 1, 0);
            else if (activeAxis == 3)
                axisDir.set(0, 0, 1);

            if (engine.state.isLocalGizmo) {
                org.joml.Matrix4f rotMat = new org.joml.Matrix4f().rotationXYZ(selObj.rotation.x, selObj.rotation.y,
                        selObj.rotation.z);
                axisDir.mulDirection(rotMat);
            }

            // 3. 3D 축이 현재 화면에서 어느 방향으로 향하는지 투영값(Dot Product) 계산
            float screenProjX = axisDir.x * camRightX + axisDir.z * camRightZ;
            float screenProjZ = axisDir.x * camForwardX + axisDir.z * camForwardZ;

            // 마우스 움직임과 화면 투영 방향을 결합하여 직관적인 입력값 생성
            float mouseInput = (deltaX * screenProjX + (-deltaY) * screenProjZ) * sensitivity;
            float mouseInputY = (-deltaY) * sensitivity;

            // ==========================================
            // 기즈모 모드별 드래그 변형 분기
            // ==========================================
            if (engine.state.gizmoMode == 0) {
                // --- 1. 위치 (Position) : 마우스 이동을 화면 기준 3D 벡터로 만든 뒤
                // axisDir(로컬/글로벌이 반영된 축 방향)에 투영해서 이동량 계산.
                // → Local 모드에서 오브젝트가 회전해 있어도 Y축이 진짜 로컬 Y 방향으로 움직임
                org.joml.Vector3f camRight = new org.joml.Vector3f(camRightX, 0, camRightZ);
                org.joml.Vector3f camUp = new org.joml.Vector3f(0, 1, 0);

                org.joml.Vector3f screenDelta = new org.joml.Vector3f(camRight).mul(deltaX * sensitivity)
                        .add(new org.joml.Vector3f(camUp).mul(-deltaY * sensitivity));

                float moveAmount = screenDelta.dot(axisDir);
                org.joml.Vector3f moveDelta = new org.joml.Vector3f(axisDir).mul(moveAmount);

                selObj.pos.add(moveDelta);

            } else if (engine.state.gizmoMode == 1) {
                // --- 2. 크기 (Scale) ---
                if (activeAxis == 1)
                    selObj.size.x = Math.max(0.1f, selObj.size.x + mouseInput);
                else if (activeAxis == 2)
                    selObj.size.y = Math.max(0.1f, selObj.size.y + mouseInputY);
                else if (activeAxis == 3)
                    selObj.size.z = Math.max(0.1f, selObj.size.z + mouseInput);

            } else if (engine.state.gizmoMode == 2) {
                // --- 3. 회전 (Rotation) ---
                float rotSpeed = 0.015f;
                org.joml.Quaternionf currentQuat = new org.joml.Quaternionf().rotationXYZ(selObj.rotation.x,
                        selObj.rotation.y, selObj.rotation.z);
                org.joml.Quaternionf deltaQuat = new org.joml.Quaternionf();

                if (activeAxis == 1)
                    deltaQuat.rotationX(deltaX * rotSpeed);
                else if (activeAxis == 2)
                    deltaQuat.rotationY(-deltaY * rotSpeed);
                else if (activeAxis == 3)
                    deltaQuat.rotationZ(deltaX * rotSpeed);

                if (engine.state.isLocalGizmo) {
                    currentQuat.mul(deltaQuat);
                } else {
                    deltaQuat.mul(currentQuat);
                    currentQuat = deltaQuat;
                }

                org.joml.Vector3f newEuler = new org.joml.Vector3f();
                currentQuat.getEulerAnglesXYZ(newEuler);
                selObj.rotation.set(newEuler);
            }

            lastMouseX = mouseX[0];
            lastMouseY = mouseY[0];
        }
        // 3. 마우스를 뗄 때
        else if (!currentPressed) {
            activeAxis = 0;
        }

        engine.state.activeGizmoAxis = activeAxis;
        engine.state.prevMousePressed = currentPressed;
    }

    private void checkGizmoClick() {
        if (engine.state.selectedObjectIndex < 0 || engine.state.selectedObjectIndex >= engine.state.objects.size()) {
            return;
        }

        LevelObject selObj = engine.state.objects.get(engine.state.selectedObjectIndex);
        Vector3f objPos = new Vector3f(selObj.pos.x, selObj.pos.y, selObj.pos.z);

        Ray ray = getMouseRay();
        if (ray == null)
            return;

        // [핵심] Local 모드일 때는 마우스 광선(Ray)을 오브젝트 회전의 반대(Inverse)로 돌려버림.
        // 이렇게 하면 기울어진 기즈모를 원래의 수직/수평 박스 계산식 그대로 정확히 클릭할 수 있어!
        Ray pickingRay = ray;
        if (engine.state.isLocalGizmo) {
            org.joml.Matrix4f invRot = new org.joml.Matrix4f()
                    .rotationXYZ(selObj.rotation.x, selObj.rotation.y, selObj.rotation.z)
                    .invert();

            Vector3f localOrigin = new Vector3f(ray.origin).sub(objPos).mulPosition(invRot).add(objPos);
            Vector3f localDir = new Vector3f(ray.direction).mulDirection(invRot).normalize();

            pickingRay = new Ray(localOrigin, localDir);
        }

        int hitAxis = 0;
        float closestDist = Float.MAX_VALUE;

        // ==========================================
        // 1. 회전 모드 (gizmoMode == 2) : 링(원) 형태 피킹
        // ==========================================
        if (engine.state.gizmoMode == 2) {
            float radius = 2.0f;
            float thickness = 0.3f;

            if (Math.abs(pickingRay.direction.x) > 1e-6f) {
                float t = (objPos.x - pickingRay.origin.x) / pickingRay.direction.x;
                if (t > 0) {
                    Vector3f hit = new Vector3f(pickingRay.origin).add(new Vector3f(pickingRay.direction).mul(t));
                    float dy = hit.y - objPos.y;
                    float dz = hit.z - objPos.z;
                    float distFromCenter = (float) Math.sqrt(dy * dy + dz * dz);
                    if (Math.abs(distFromCenter - radius) <= thickness) {
                        if (t < closestDist) {
                            closestDist = t;
                            hitAxis = 1;
                        }
                    }
                }
            }

            if (Math.abs(pickingRay.direction.y) > 1e-6f) {
                float t = (objPos.y - pickingRay.origin.y) / pickingRay.direction.y;
                if (t > 0) {
                    Vector3f hit = new Vector3f(pickingRay.origin).add(new Vector3f(pickingRay.direction).mul(t));
                    float dx = hit.x - objPos.x;
                    float dz = hit.z - objPos.z;
                    float distFromCenter = (float) Math.sqrt(dx * dx + dz * dz);
                    if (Math.abs(distFromCenter - radius) <= thickness) {
                        if (t < closestDist) {
                            closestDist = t;
                            hitAxis = 2;
                        }
                    }
                }
            }

            if (Math.abs(pickingRay.direction.z) > 1e-6f) {
                float t = (objPos.z - pickingRay.origin.z) / pickingRay.direction.z;
                if (t > 0) {
                    Vector3f hit = new Vector3f(pickingRay.origin).add(new Vector3f(pickingRay.direction).mul(t));
                    float dx = hit.x - objPos.x;
                    float dy = hit.y - objPos.y;
                    float distFromCenter = (float) Math.sqrt(dx * dx + dy * dy);
                    if (Math.abs(distFromCenter - radius) <= thickness) {
                        if (t < closestDist) {
                            closestDist = t;
                            hitAxis = 3;
                        }
                    }
                }
            }

        }
        // ==========================================
        // 2. 위치 및 크기 모드 (gizmoMode == 0, 1) : 박스 피킹
        // ==========================================
        else {
            float length = 2.0f;
            float thickness = 0.3f;

            float xMinX = objPos.x, xMaxX = objPos.x + length;
            float xMinY = objPos.y - thickness, xMaxY = objPos.y + thickness;
            float xMinZ = objPos.z - thickness, xMaxZ = objPos.z + thickness;
            float distDistX = getRayBoxIntersectionDistance(pickingRay.origin, pickingRay.direction, xMinX, xMaxX,
                    xMinY, xMaxY, xMinZ, xMaxZ);

            float yMinX = objPos.x - thickness, yMaxX = objPos.x + thickness;
            float yMinY = objPos.y, yMaxY = objPos.y + length;
            float yMinZ = objPos.z - thickness, yMaxZ = objPos.z + thickness;
            float distDistY = getRayBoxIntersectionDistance(pickingRay.origin, pickingRay.direction, yMinX, yMaxX,
                    yMinY, yMaxY, yMinZ, yMaxZ);

            float zMinX = objPos.x - thickness, zMaxX = objPos.x + thickness;
            float zMinY = objPos.y - thickness, zMaxY = objPos.y + thickness;
            float zMinZ = objPos.z, zMaxZ = objPos.z + length;
            float distDistZ = getRayBoxIntersectionDistance(pickingRay.origin, pickingRay.direction, zMinX, zMaxX,
                    zMinY, zMaxY, zMinZ, zMaxZ);

            if (distDistX >= 0 && distDistX < closestDist) {
                closestDist = distDistX;
                hitAxis = 1;
            }
            if (distDistY >= 0 && distDistY < closestDist) {
                closestDist = distDistY;
                hitAxis = 2;
            }
            if (distDistZ >= 0 && distDistZ < closestDist) {
                closestDist = distDistZ;
                hitAxis = 3;
            }
        }

        activeAxis = hitAxis;
        if (activeAxis != 0) {
            dragStartPos.set(objPos);
        }
    }

    private Ray getMouseRay() {
        double[] mouseX = new double[1];
        double[] mouseY = new double[1];
        glfwGetCursorPos(engine.state.window, mouseX, mouseY);

        int[] winWidth = new int[1];
        int[] winHeight = new int[1];
        glfwGetWindowSize(engine.state.window, winWidth, winHeight);

        if (winWidth[0] == 0 || winHeight[0] == 0)
            return null;

        float x = (float) (2.0 * mouseX[0] / winWidth[0] - 1.0);
        float y = (float) (1.0 - 2.0 * mouseY[0] / winHeight[0]);

        Vector4f rayStart = new Vector4f(x, y, -1.0f, 1.0f).mul(engine.state.editorInvVPMatrix);
        rayStart.div(rayStart.w);

        Vector4f rayEnd = new Vector4f(x, y, 1.0f, 1.0f).mul(engine.state.editorInvVPMatrix);
        rayEnd.div(rayEnd.w);

        Vector3f origin = new Vector3f(rayStart.x, rayStart.y, rayStart.z);
        Vector3f dir = new Vector3f(rayEnd.x - rayStart.x, rayEnd.y - rayStart.y, rayEnd.z - rayStart.z).normalize();

        return new Ray(origin, dir);
    }

    public float getRayBoxIntersectionDistance(Vector3f origin, Vector3f dir, float minX, float maxX, float minY,
            float maxY, float minZ, float maxZ) {
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

    private static class Ray {
        public Vector3f origin;
        public Vector3f direction;

        public Ray(Vector3f origin, Vector3f direction) {
            this.origin = origin;
            this.direction = direction;
        }
    }

    public void castRayAndSelectObject() {
        Ray ray = getMouseRay();
        if (ray == null)
            return;

        int hitTarget = -1;
        float closestDist = Float.MAX_VALUE;

        for (int i = 0; i < engine.state.objects.size(); i++) {
            LevelObject obj = engine.state.objects.get(i);

            // 객체도 회전해있을 수 있으므로 AABB 대신 OBB 피킹 방식 적용 (Ray 역회전)
            org.joml.Matrix4f invRot = new org.joml.Matrix4f()
                    .rotationXYZ(obj.rotation.x, obj.rotation.y, obj.rotation.z)
                    .invert();

            Vector3f localOrigin = new Vector3f(ray.origin).sub(obj.pos).mulPosition(invRot).add(obj.pos);
            Vector3f localDir = new Vector3f(ray.direction).mulDirection(invRot).normalize();

            float minX = obj.pos.x - obj.size.x / 2.0f;
            float maxX = obj.pos.x + obj.size.x / 2.0f;
            float minY = obj.pos.y - obj.size.y / 2.0f;
            float maxY = obj.pos.y + obj.size.y / 2.0f;
            float minZ = obj.pos.z - obj.size.z / 2.0f;
            float maxZ = obj.pos.z + obj.size.z / 2.0f;

            float dist = getRayBoxIntersectionDistance(localOrigin, localDir, minX, maxX, minY, maxY, minZ, maxZ);
            if (dist >= 0 && dist < closestDist) {
                closestDist = dist;
                hitTarget = i;
            }
        }

        engine.state.selectedObjectIndex = hitTarget;
    }
}