import imgui.ImGui;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import org.joml.Vector3f;

public class EngineUiSystem {
    private final Engine3DLWJGL engine;

    public EngineUiSystem(Engine3DLWJGL engine) {
        this.engine = engine;
    }

    public void renderImGui() {
        engine.state.imGuiGlfw.newFrame();
        ImGui.newFrame();

        ImGui.begin("Engine Control Panel");
        float fps = ImGui.getIO().getFramerate();
        float frameTime = 1000.0f / (fps > 0 ? fps : 1.0f);

        ImGui.text(String.format("FPS: %.1f", fps));
        ImGui.text(String.format("Frame Time: %.2f ms", frameTime));
        ImGui.separator();

        ImGui.text("Press TAB to toggle Cursor & UI Control");
        ImGui.separator();

        if (ImGui.checkbox("Editor Mode", engine.state.isEditorMode)) {
            if (engine.state.isEditorMode.get()) {
                engine.state.isUiMode = true;
                org.lwjgl.glfw.GLFW.glfwSetInputMode(engine.state.window, org.lwjgl.glfw.GLFW.GLFW_CURSOR, org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL);
            } else {
                engine.state.isUiMode = false;
                org.lwjgl.glfw.GLFW.glfwSetInputMode(engine.state.window, org.lwjgl.glfw.GLFW.GLFW_CURSOR, org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED);
                engine.state.lastMouseX = -1;
                engine.state.lastMouseY = -1;
            }
        }

        if (ImGui.checkbox("Orthographic Mode", engine.state.isOrthographic)) { }

        ImGui.separator();

        float[] lp = { engine.state.lightPos.x, engine.state.lightPos.y, engine.state.lightPos.z };
        if (ImGui.sliderFloat3("Light Position", lp, -20.0f, 20.0f)) {
            engine.state.lightPos.set(lp[0], lp[1], lp[2]);
        }

        float[] la = { engine.state.lightAmbient };
        if (ImGui.sliderFloat("Ambient Light", la, 0.0f, 1.0f)) {
            engine.state.lightAmbient = la[0];
        }

        float[] ld = { engine.state.lightDiffuse };
        if (ImGui.sliderFloat("Diffuse Light", ld, 0.0f, 1.0f)) {
            engine.state.lightDiffuse = ld[0];
        }

        ImGui.separator();

        float[] jp = { engine.state.jumpStrength };
        if (ImGui.sliderFloat("Jump Strength", jp, 0.1f, 0.8f)) {
            engine.state.jumpStrength = jp[0];
        }

        float[] gr = { engine.state.gravity };
        if (ImGui.sliderFloat("Gravity", gr, -0.05f, -0.001f)) {
            engine.state.gravity = gr[0];
        }

        float[] cd = { engine.state.camDistance };
        if (ImGui.sliderFloat("Camera Distance", cd, 2.0f, 15.0f)) {
            engine.state.camDistance = cd[0];
        }

        ImGui.separator();
        ImGui.text("Camera Settings");

        float[] spdArr = { engine.state.editorSpeed };
        if (ImGui.sliderFloat("Base Speed", spdArr, 0.05f, 1.0f)) {
            engine.state.editorSpeed = spdArr[0];
        }

        float[] multArr = { engine.state.editorSpeedMultiplier };
        if (ImGui.sliderFloat("Ctrl Multiplier", multArr, 1.0f, 5.0f)) {
            engine.state.editorSpeedMultiplier = multArr[0];
        }

        ImGui.end();

        ImGui.begin("Level Editor");
        if (engine.state.isEditorMode.get()) {
            ImGui.text("Editor Mode Active");
            ImGui.separator();

            // [추가] 기즈모 모드 선택 UI (숫자키 1, 2, 3과 연동)
            ImGui.text("Gizmo Mode (1:Pos, 2:Scale, 3:Rot):");
            if (ImGui.radioButton("Position [1]", engine.state.gizmoMode == 0)) {
                engine.state.gizmoMode = 0;
            }
            ImGui.sameLine();
            if (ImGui.radioButton("Scale [2]", engine.state.gizmoMode == 1)) {
                engine.state.gizmoMode = 1;
            }
            ImGui.sameLine();
            if (ImGui.radioButton("Rotation [3]", engine.state.gizmoMode == 2)) {
                engine.state.gizmoMode = 2;
            }
            ImGui.separator();

            ImGui.text("Spawn Panel");
            if (ImGui.button("small block")) {
                engine.state.objects.add(new BlockObject(new Vector3f(engine.state.playerObject.pos.x, engine.state.playerObject.pos.y, engine.state.playerObject.pos.z - 3.0f), new Vector3f(1.0f, 1.0f, 1.0f)));
                engine.state.selectedObjectIndex = engine.state.objects.size() - 1;
            }
            ImGui.sameLine();
            if (ImGui.button("big block")) {
                engine.state.objects.add(new BlockObject(new Vector3f(engine.state.playerObject.pos.x, engine.state.playerObject.pos.y, engine.state.playerObject.pos.z - 3.0f), new Vector3f(4.0f, 2.0f, 4.0f)));
                engine.state.selectedObjectIndex = engine.state.objects.size() - 1;
            }

            if (ImGui.button("ground block")) {
                engine.state.objects.add(new BlockObject(new Vector3f(engine.state.playerObject.pos.x, engine.state.playerObject.pos.y - 1.0f, engine.state.playerObject.pos.z - 3.0f), new Vector3f(10.0f, 0.5f, 10.0f)));
                engine.state.selectedObjectIndex = engine.state.objects.size() - 1;
            }

            ImGui.separator();
            ImGui.text("Object List");
            ImGui.beginChild("ObjectListRegion", 0, 150, true);

            for (int i = 0; i < engine.state.objects.size(); i++) {
                LevelObject obj = engine.state.objects.get(i);
                boolean isSelected = (engine.state.selectedObjectIndex == i);

                if (ImGui.selectable(obj.getDisplayName() + " [" + i + "]", isSelected)) {
                    engine.state.selectedObjectIndex = i;
                }
            }
            ImGui.endChild();

            ImGui.separator();
            ImGui.text("Inspector");

            if (engine.state.selectedObjectIndex == -1 || engine.state.selectedObjectIndex >= engine.state.objects.size()) {
                ImGui.textDisabled("No object selected.");
            } else {
                LevelObject selObj = engine.state.objects.get(engine.state.selectedObjectIndex);
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
                    (float)Math.toDegrees(selObj.rotation.x),
                    (float)Math.toDegrees(selObj.rotation.y),
                    (float)Math.toDegrees(selObj.rotation.z)
                };
                if (ImGui.dragFloat3("Rotation##Object", oRot, 1.0f, -360.0f, 360.0f)) {
                    selObj.rotation.set(
                        (float)Math.toRadians(oRot[0]),
                        (float)Math.toRadians(oRot[1]),
                        (float)Math.toRadians(oRot[2])
                    );
                }

                if (engine.state.selectedObjectIndex > 0) {
                    if (ImGui.button("Delete Object")) {
                        engine.state.objects.remove(engine.state.selectedObjectIndex);
                        engine.state.selectedObjectIndex = -1;
                    }
                } else {
                    ImGui.textDisabled("Cannot delete Player entity.");
                }
            }
        } else {
            ImGui.text("Switch to Editor Mode to edit levels.");
        }

        ImGui.end();

        ImGui.render();
        engine.state.imGuiGl3.renderDrawData(ImGui.getDrawData());
    }
}