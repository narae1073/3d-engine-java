import imgui.ImGui;
import org.joml.Vector3f;

public class EngineUiSystem implements EngineSystem {
    private final EngineState state;   // ImGui 백엔드 + 윈도우 접근용
    private final CameraState camera;
    private final LightState light;
    private final PhysicsState physics;
    private final EditorState editor;
    private final WorldState world;

    public EngineUiSystem(EngineState state, CameraState camera, LightState light,
                          PhysicsState physics, EditorState editor, WorldState world) {
        this.state = state;
        this.camera = camera;
        this.light = light;
        this.physics = physics;
        this.editor = editor;
        this.world = world;
    }

    @Override
    public void render() {
        // ============================================================
        // ImGui 프레임 시작 (필수!)
        // ============================================================
        state.imGuiGlfw.newFrame();
        ImGui.newFrame();

        // ============================================================
        // 1. Engine Control Panel
        // ============================================================
        ImGui.begin("Engine Control Panel");
        float fps = ImGui.getIO().getFramerate();
        float frameTime = 1000.0f / (fps > 0 ? fps : 1.0f);

        ImGui.text(String.format("FPS: %.1f", fps));
        ImGui.text(String.format("Frame Time: %.2f ms", frameTime));
        ImGui.separator();

        ImGui.text("Press TAB to toggle Cursor & UI Control");
        ImGui.separator();

        // 에디터 모드 토글
        if (ImGui.checkbox("Editor Mode", camera.isEditorMode)) {
            if (camera.isEditorMode.get()) {
                state.input.isUiMode = true;
                org.lwjgl.glfw.GLFW.glfwSetInputMode(
                        state.window.window,
                        org.lwjgl.glfw.GLFW.GLFW_CURSOR,
                        org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL);
            } else {
                state.input.isUiMode = false;
                org.lwjgl.glfw.GLFW.glfwSetInputMode(
                        state.window.window,
                        org.lwjgl.glfw.GLFW.GLFW_CURSOR,
                        org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED);
                state.input.lastMouseX = -1;
                state.input.lastMouseY = -1;
            }
        }

        ImGui.checkbox("Orthographic Mode", camera.isOrthographic);

        ImGui.separator();

        // 조명
        float[] lp = { light.lightPos.x, light.lightPos.y, light.lightPos.z };
        if (ImGui.sliderFloat3("Light Position", lp, -20.0f, 20.0f)) {
            light.lightPos.set(lp[0], lp[1], lp[2]);
        }

        float[] la = { light.lightAmbient };
        if (ImGui.sliderFloat("Ambient Light", la, 0.0f, 1.0f)) {
            light.lightAmbient = la[0];
        }

        float[] ld = { light.lightDiffuse };
        if (ImGui.sliderFloat("Diffuse Light", ld, 0.0f, 1.0f)) {
            light.lightDiffuse = ld[0];
        }

        ImGui.separator();

        // 물리
        float[] jp = { physics.jumpStrength };
        if (ImGui.sliderFloat("Jump Strength", jp, 0.1f, 0.8f)) {
            physics.jumpStrength = jp[0];
        }

        float[] gr = { physics.gravity };
        if (ImGui.sliderFloat("Gravity", gr, -0.05f, -0.001f)) {
            physics.gravity = gr[0];
        }

        float[] cd = { camera.camDistance };
        if (ImGui.sliderFloat("Camera Distance", cd, 2.0f, 15.0f)) {
            camera.camDistance = cd[0];
        }

        ImGui.separator();
        ImGui.text("Camera Settings");

        float[] spdArr = { camera.editorSpeed };
        if (ImGui.sliderFloat("Base Speed", spdArr, 0.05f, 1.0f)) {
            camera.editorSpeed = spdArr[0];
        }

        float[] multArr = { camera.editorSpeedMultiplier };
        if (ImGui.sliderFloat("Ctrl Multiplier", multArr, 1.0f, 5.0f)) {
            camera.editorSpeedMultiplier = multArr[0];
        }

        ImGui.end();

        // ============================================================
        // 2. Level Editor
        // ============================================================
        ImGui.begin("Level Editor");
        if (camera.isEditorMode.get()) {
            ImGui.text("Editor Mode Active");
            ImGui.separator();

            // 기즈모 모드 선택
            ImGui.text("Gizmo Mode (1:Pos, 2:Scale, 3:Rot):");
            if (ImGui.radioButton("Position [1]", editor.gizmoMode == 0)) {
                editor.gizmoMode = 0;
            }
            ImGui.sameLine();
            if (ImGui.radioButton("Scale [2]", editor.gizmoMode == 1)) {
                editor.gizmoMode = 1;
            }
            ImGui.sameLine();
            if (ImGui.radioButton("Rotation [3]", editor.gizmoMode == 2)) {
                editor.gizmoMode = 2;
            }
            ImGui.separator();

            // 스폰 패널
            ImGui.text("Spawn Panel");
            if (ImGui.button("small block")) {
                world.objects.add(new BlockObject(
                        new Vector3f(world.playerObject.pos.x,
                                     world.playerObject.pos.y,
                                     world.playerObject.pos.z - 3.0f),
                        new Vector3f(1.0f, 1.0f, 1.0f)));
                editor.selectedObjectIndex = world.objects.size() - 1;
            }
            ImGui.sameLine();
            if (ImGui.button("big block")) {
                world.objects.add(new BlockObject(
                        new Vector3f(world.playerObject.pos.x,
                                     world.playerObject.pos.y,
                                     world.playerObject.pos.z - 3.0f),
                        new Vector3f(4.0f, 2.0f, 4.0f)));
                editor.selectedObjectIndex = world.objects.size() - 1;
            }

            if (ImGui.button("ground block")) {
                world.objects.add(new BlockObject(
                        new Vector3f(world.playerObject.pos.x,
                                     world.playerObject.pos.y - 1.0f,
                                     world.playerObject.pos.z - 3.0f),
                        new Vector3f(10.0f, 0.5f, 10.0f)));
                editor.selectedObjectIndex = world.objects.size() - 1;
            }

            ImGui.separator();
            ImGui.text("Object List");
            ImGui.beginChild("ObjectListRegion", 0, 150, true);

            for (int i = 0; i < world.objects.size(); i++) {
                LevelObject obj = world.objects.get(i);
                boolean isSelected = (editor.selectedObjectIndex == i);

                if (ImGui.selectable(obj.getDisplayName() + " [" + i + "]", isSelected)) {
                    editor.selectedObjectIndex = i;
                }
            }
            ImGui.endChild();

            ImGui.separator();
            ImGui.text("Inspector");

            if (editor.selectedObjectIndex == -1
                    || editor.selectedObjectIndex >= world.objects.size()) {
                ImGui.textDisabled("No object selected.");
            } else {
                LevelObject selObj = world.objects.get(editor.selectedObjectIndex);
                ImGui.text("Selected: " + selObj.getDisplayName());

                float[] oPos = { selObj.pos.x, selObj.pos.y, selObj.pos.z };
                if (ImGui.dragFloat3("Position##Object", oPos, 0.1f)) {
                    selObj.pos.set(oPos[0], oPos[1], oPos[2]);
                }

                float[] oSize = { selObj.size.x, selObj.size.y, selObj.size.z };
                if (ImGui.dragFloat3("Size##Object", oSize, 0.05f, 0.1f, 20.0f)) {
                    selObj.size.set(oSize[0], oSize[1], oSize[2]);
                }

                float[] oRot = {
                    (float) Math.toDegrees(selObj.rotation.x),
                    (float) Math.toDegrees(selObj.rotation.y),
                    (float) Math.toDegrees(selObj.rotation.z)
                };
                if (ImGui.dragFloat3("Rotation##Object", oRot, 1.0f, -360.0f, 360.0f)) {
                    selObj.rotation.set(
                            (float) Math.toRadians(oRot[0]),
                            (float) Math.toRadians(oRot[1]),
                            (float) Math.toRadians(oRot[2]));
                }

                if (editor.selectedObjectIndex > 0) {
                    if (ImGui.button("Delete Object")) {
                        world.objects.remove(editor.selectedObjectIndex);
                        editor.selectedObjectIndex = -1;
                    }
                } else {
                    ImGui.textDisabled("Cannot delete Player entity.");
                }
            }
        } else {
            ImGui.text("Switch to Editor Mode to edit levels.");
        }

        ImGui.end();

        // ============================================================
        // ImGui 프레임 종료 및 렌더 (필수!)
        // ============================================================
        ImGui.render();
        state.imGuiGl3.renderDrawData(ImGui.getDrawData());
    }
}