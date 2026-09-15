import imgui.ImGui;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import static org.lwjgl.glfw.GLFW.*;

public class EditorGizmoSystem implements EngineSystem {
    private final WindowContext win;
    private final CameraState camera;
    private final EditorState editor;
    private final WorldState world;

    private int activeAxis = 0;
    private final Vector3f dragStartPos = new Vector3f();
    private double lastMouseX = 0;
    private double lastMouseY = 0;
    private float orbitDistance = 5.0f;
    private boolean prevRightMousePressed = false;
    private boolean fKeyPressed = false;
    private boolean lKeyPressed = false;

    public EditorGizmoSystem(WindowContext win, CameraState camera, EditorState editor, WorldState world) {
        this.win = win;
        this.camera = camera;
        this.editor = editor;
        this.world = world;
    }

    @Override
    public void update(float dt) {
        if (!camera.isEditorMode.get() || ImGui.getIO().getWantCaptureMouse()) {
            editor.prevMousePressed = false;
            editor.activeGizmoAxis = 0;
            return;
        }

        // 숫자키 1, 2, 3으로 기즈모 모드 변경
        if (glfwGetKey(win.window, GLFW_KEY_1) == GLFW_PRESS)
            editor.gizmoMode = 0;
        else if (glfwGetKey(win.window, GLFW_KEY_2) == GLFW_PRESS)
            editor.gizmoMode = 1;
        else if (glfwGetKey(win.window, GLFW_KEY_3) == GLFW_PRESS)
            editor.gizmoMode = 2;

        // F키 포커스
        if (glfwGetKey(win.window, GLFW_KEY_F) == GLFW_PRESS) {
            if (!fKeyPressed) {
                fKeyPressed = true;
                if (editor.selectedObjectIndex >= 0 && editor.selectedObjectIndex < world.objects.size()) {
                    LevelObject selObj = world.objects.get(editor.selectedObjectIndex);
                    float focusDistance = Math.max(3.0f, selObj.size.length() * 2.0f);
                    float fx = (float) (Math.sin(camera.editorCamYaw) * Math.cos(camera.editorCamPitch));
                    float fy = (float) -Math.sin(camera.editorCamPitch);
                    float fz = (float) (-Math.cos(camera.editorCamYaw) * Math.cos(camera.editorCamPitch));
                    camera.editorCamPos.set(
                            selObj.pos.x - fx * focusDistance,
                            selObj.pos.y - fy * focusDistance,
                            selObj.pos.z - fz * focusDistance);
                }
            }
        } else {
            fKeyPressed = false;
        }

        // L키 Local/Global 토글
        if (glfwGetKey(win.window, GLFW_KEY_L) == GLFW_PRESS) {
            if (!lKeyPressed) {
                editor.isLocalGizmo = !editor.isLocalGizmo;
                lKeyPressed = true;
                System.out.println("기즈모 모드: " + (editor.isLocalGizmo ? "Local" : "Global"));
            }
        } else {
            lKeyPressed = false;
        }

        // Alt + 우클릭 오빗
        boolean isOrbitAltPressed = (glfwGetKey(win.window, GLFW_KEY_LEFT_ALT) == GLFW_PRESS) ||
                (glfwGetKey(win.window, GLFW_KEY_RIGHT_ALT) == GLFW_PRESS);
        boolean isRightMousePressed = isOrbitAltPressed &&
                (glfwGetMouseButton(win.window, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS);

        if (isRightMousePressed) {
            if (editor.selectedObjectIndex >= 0 && editor.selectedObjectIndex < world.objects.size()) {
                LevelObject selObj = world.objects.get(editor.selectedObjectIndex);
                double[] mouseX = new double[1], mouseY = new double[1];
                glfwGetCursorPos(win.window, mouseX, mouseY);

                if (!prevRightMousePressed) {
                    lastMouseX = mouseX[0];
                    lastMouseY = mouseY[0];
                    orbitDistance = new Vector3f(camera.editorCamPos).sub(selObj.pos).length();
                    if (orbitDistance < 1.0f)
                        orbitDistance = 5.0f;
                }

                float deltaX = (float) (mouseX[0] - lastMouseX);
                float deltaY = (float) (mouseY[0] - lastMouseY);
                float rotSpeed = 0.005f;
                camera.editorCamYaw += deltaX * rotSpeed;
                camera.editorCamPitch -= deltaY * rotSpeed;
                camera.editorCamPitch = Math.max(-1.5f, Math.min(1.5f, camera.editorCamPitch));

                float wheel = ImGui.getIO().getMouseWheel();
                if (wheel != 0) {
                    orbitDistance -= wheel * (orbitDistance * 0.15f);
                    orbitDistance = Math.max(0.5f, Math.min(50.0f, orbitDistance));
                }

                float fx = (float) (Math.sin(camera.editorCamYaw) * Math.cos(camera.editorCamPitch));
                float fy = (float) -Math.sin(camera.editorCamPitch);
                float fz = (float) (-Math.cos(camera.editorCamYaw) * Math.cos(camera.editorCamPitch));

                camera.editorCamPos.set(
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

        boolean currentPressed = (glfwGetMouseButton(win.window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS);

        if (currentPressed && !editor.prevMousePressed) {
            checkGizmoClick();
            if (activeAxis == 0) {
                castRayAndSelectObject();
            } else {
                double[] mouseX = new double[1], mouseY = new double[1];
                glfwGetCursorPos(win.window, mouseX, mouseY);
                lastMouseX = mouseX[0];
                lastMouseY = mouseY[0];
            }
        } else if (currentPressed && editor.prevMousePressed && activeAxis != 0) {
            double[] mouseX = new double[1], mouseY = new double[1];
            glfwGetCursorPos(win.window, mouseX, mouseY);

            float deltaX = (float) (mouseX[0] - lastMouseX);
            float deltaY = (float) (mouseY[0] - lastMouseY);

            LevelObject selObj = world.objects.get(editor.selectedObjectIndex);
            float sensitivity = 0.02f;

            float camYaw = camera.editorCamYaw;
            float camRightX = (float) Math.cos(camYaw);
            float camRightZ = (float) Math.sin(camYaw);
            float camForwardX = (float) Math.sin(camYaw);
            float camForwardZ = (float) -Math.cos(camYaw);

            Vector3f axisDir = new Vector3f();
            if (activeAxis == 1)
                axisDir.set(1, 0, 0);
            else if (activeAxis == 2)
                axisDir.set(0, 1, 0);
            else if (activeAxis == 3)
                axisDir.set(0, 0, 1);

            if (editor.isLocalGizmo) {
                Matrix4f rotMat = new Matrix4f().rotationXYZ(
                        selObj.rotation.x, selObj.rotation.y, selObj.rotation.z);
                axisDir.mulDirection(rotMat);
            }

            float screenProjX = axisDir.x * camRightX + axisDir.z * camRightZ;
            float screenProjZ = axisDir.x * camForwardX + axisDir.z * camForwardZ;
            float mouseInput = (deltaX * screenProjX + (-deltaY) * screenProjZ) * sensitivity;
            float mouseInputY = (-deltaY) * sensitivity;

            if (editor.gizmoMode == 0) {
                Vector3f camRight = new Vector3f(camRightX, 0, camRightZ);
                Vector3f camUp = new Vector3f(0, 1, 0);
                Vector3f screenDelta = new Vector3f(camRight).mul(deltaX * sensitivity)
                        .add(new Vector3f(camUp).mul(-deltaY * sensitivity));
                float moveAmount = screenDelta.dot(axisDir);
                selObj.pos.add(new Vector3f(axisDir).mul(moveAmount));

            } else if (editor.gizmoMode == 1) {
                if (activeAxis == 1)
                    selObj.size.x = Math.max(0.1f, selObj.size.x + mouseInput);
                else if (activeAxis == 2)
                    selObj.size.y = Math.max(0.1f, selObj.size.y + mouseInputY);
                else if (activeAxis == 3)
                    selObj.size.z = Math.max(0.1f, selObj.size.z + mouseInput);

            } else if (editor.gizmoMode == 2) {
                float rotSpeed = 0.015f;
                Quaternionf currentQuat = new Quaternionf().rotationXYZ(
                        selObj.rotation.x, selObj.rotation.y, selObj.rotation.z);
                Quaternionf deltaQuat = new Quaternionf();
                if (activeAxis == 1)
                    deltaQuat.rotationX(deltaX * rotSpeed);
                else if (activeAxis == 2)
                    deltaQuat.rotationY(-deltaY * rotSpeed);
                else if (activeAxis == 3)
                    deltaQuat.rotationZ(deltaX * rotSpeed);

                if (editor.isLocalGizmo) {
                    currentQuat.mul(deltaQuat);
                } else {
                    deltaQuat.mul(currentQuat);
                    currentQuat = deltaQuat;
                }
                Vector3f newEuler = new Vector3f();
                currentQuat.getEulerAnglesXYZ(newEuler);
                selObj.rotation.set(newEuler);
            }

            lastMouseX = mouseX[0];
            lastMouseY = mouseY[0];
        } else if (!currentPressed) {
            activeAxis = 0;
        }

        editor.activeGizmoAxis = activeAxis;
        editor.prevMousePressed = currentPressed;
    }

    // ============================================================
    // 기즈모 클릭 판정
    // ============================================================
    private void checkGizmoClick() {
        if (editor.selectedObjectIndex < 0 || editor.selectedObjectIndex >= world.objects.size()) {
            return;
        }

        LevelObject selObj = world.objects.get(editor.selectedObjectIndex);
        Vector3f objPos = new Vector3f(selObj.pos.x, selObj.pos.y, selObj.pos.z);

        Ray ray = getMouseRay();
        if (ray == null)
            return;

        // [핵심] Local 모드일 때는 마우스 광선(Ray)을 오브젝트 회전의 반대(Inverse)로 돌려버림.
        // 이렇게 하면 기울어진 기즈모를 원래의 수직/수평 박스 계산식 그대로 정확히 클릭할 수 있음.
        Ray pickingRay = ray;
        if (editor.isLocalGizmo) {
            Matrix4f invRot = new Matrix4f()
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
        if (editor.gizmoMode == 2) {
            float radius = 2.0f;
            float thickness = 0.3f;

            // --- X축 링 (YZ 평면) ---
            if (Math.abs(pickingRay.direction.x) > 1e-6f) {
                float t = (objPos.x - pickingRay.origin.x) / pickingRay.direction.x;
                if (t > 0) {
                    Vector3f hit = new Vector3f(pickingRay.origin)
                            .add(new Vector3f(pickingRay.direction).mul(t));
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

            // --- Y축 링 (XZ 평면) ---
            if (Math.abs(pickingRay.direction.y) > 1e-6f) {
                float t = (objPos.y - pickingRay.origin.y) / pickingRay.direction.y;
                if (t > 0) {
                    Vector3f hit = new Vector3f(pickingRay.origin)
                            .add(new Vector3f(pickingRay.direction).mul(t));
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

            // --- Z축 링 (XY 평면) ---
            if (Math.abs(pickingRay.direction.z) > 1e-6f) {
                float t = (objPos.z - pickingRay.origin.z) / pickingRay.direction.z;
                if (t > 0) {
                    Vector3f hit = new Vector3f(pickingRay.origin)
                            .add(new Vector3f(pickingRay.direction).mul(t));
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

            // X축 박스 (X방향으로 뻗은 얇은 상자)
            float xMinX = objPos.x, xMaxX = objPos.x + length;
            float xMinY = objPos.y - thickness, xMaxY = objPos.y + thickness;
            float xMinZ = objPos.z - thickness, xMaxZ = objPos.z + thickness;
            float distX = getRayBoxIntersectionDistance(
                    pickingRay.origin, pickingRay.direction,
                    xMinX, xMaxX, xMinY, xMaxY, xMinZ, xMaxZ);

            // Y축 박스
            float yMinX = objPos.x - thickness, yMaxX = objPos.x + thickness;
            float yMinY = objPos.y, yMaxY = objPos.y + length;
            float yMinZ = objPos.z - thickness, yMaxZ = objPos.z + thickness;
            float distY = getRayBoxIntersectionDistance(
                    pickingRay.origin, pickingRay.direction,
                    yMinX, yMaxX, yMinY, yMaxY, yMinZ, yMaxZ);

            // Z축 박스
            float zMinX = objPos.x - thickness, zMaxX = objPos.x + thickness;
            float zMinY = objPos.y - thickness, zMaxY = objPos.y + thickness;
            float zMinZ = objPos.z, zMaxZ = objPos.z + length;
            float distZ = getRayBoxIntersectionDistance(
                    pickingRay.origin, pickingRay.direction,
                    zMinX, zMaxX, zMinY, zMaxY, zMinZ, zMaxZ);

            if (distX >= 0 && distX < closestDist) {
                closestDist = distX;
                hitAxis = 1;
            }
            if (distY >= 0 && distY < closestDist) {
                closestDist = distY;
                hitAxis = 2;
            }
            if (distZ >= 0 && distZ < closestDist) {
                closestDist = distZ;
                hitAxis = 3;
            }
        }

        activeAxis = hitAxis;
        if (activeAxis != 0) {
            dragStartPos.set(objPos);
        }
    }

    private Ray getMouseRay() {
        double[] mouseX = new double[1], mouseY = new double[1];
        glfwGetCursorPos(win.window, mouseX, mouseY);

        int[] winWidth = new int[1], winHeight = new int[1];
        glfwGetWindowSize(win.window, winWidth, winHeight);
        if (winWidth[0] == 0 || winHeight[0] == 0)
            return null;

        float x = (float) (2.0 * mouseX[0] / winWidth[0] - 1.0);
        float y = (float) (1.0 - 2.0 * mouseY[0] / winHeight[0]);

        Vector4f rayStart = new Vector4f(x, y, -1.0f, 1.0f).mul(camera.editorInvVPMatrix);
        rayStart.div(rayStart.w);

        Vector4f rayEnd = new Vector4f(x, y, 1.0f, 1.0f).mul(camera.editorInvVPMatrix);
        rayEnd.div(rayEnd.w);

        Vector3f origin = new Vector3f(rayStart.x, rayStart.y, rayStart.z);
        Vector3f dir = new Vector3f(
                rayEnd.x - rayStart.x,
                rayEnd.y - rayStart.y,
                rayEnd.z - rayStart.z).normalize();

        return new Ray(origin, dir);
    }

    public float getRayBoxIntersectionDistance(Vector3f origin, Vector3f dir, float minX, float maxX,
            float minY, float maxY, float minZ, float maxZ) {
        float t1 = (minX - origin.x) / dir.x, t2 = (maxX - origin.x) / dir.x;
        float t3 = (minY - origin.y) / dir.y, t4 = (maxY - origin.y) / dir.y;
        float t5 = (minZ - origin.z) / dir.z, t6 = (maxZ - origin.z) / dir.z;
        float tmin = Math.max(Math.max(Math.min(t1, t2), Math.min(t3, t4)), Math.min(t5, t6));
        float tmax = Math.min(Math.min(Math.max(t1, t2), Math.max(t3, t4)), Math.max(t5, t6));
        if (tmax < 0 || tmin > tmax)
            return -1.0f;
        return (tmin < 0) ? tmax : tmin;
    }

    private static class Ray {
        public Vector3f origin, direction;

        public Ray(Vector3f o, Vector3f d) {
            origin = o;
            direction = d;
        }
    }

    public void castRayAndSelectObject() {
        Ray ray = getMouseRay();
        if (ray == null)
            return;

        int hitTarget = -1;
        float closestDist = Float.MAX_VALUE;

        for (int i = 0; i < world.objects.size(); i++) {
            LevelObject obj = world.objects.get(i);
            Matrix4f invRot = new Matrix4f().rotationXYZ(
                    obj.rotation.x, obj.rotation.y, obj.rotation.z).invert();
            Vector3f localOrigin = new Vector3f(ray.origin).sub(obj.pos).mulPosition(invRot).add(obj.pos);
            Vector3f localDir = new Vector3f(ray.direction).mulDirection(invRot).normalize();

            float minX = obj.pos.x - obj.size.x / 2.0f, maxX = obj.pos.x + obj.size.x / 2.0f;
            float minY = obj.pos.y - obj.size.y / 2.0f, maxY = obj.pos.y + obj.size.y / 2.0f;
            float minZ = obj.pos.z - obj.size.z / 2.0f, maxZ = obj.pos.z + obj.size.z / 2.0f;

            float dist = getRayBoxIntersectionDistance(localOrigin, localDir, minX, maxX, minY, maxY, minZ, maxZ);
            if (dist >= 0 && dist < closestDist) {
                closestDist = dist;
                hitTarget = i;
            }
        }
        editor.selectedObjectIndex = hitTarget;
    }
}