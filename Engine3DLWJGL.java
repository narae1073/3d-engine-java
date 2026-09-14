import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Engine3DLWJGL {
    private long window;
    private int width = 800;
    private int height = 600;

    // 카메라 위치 (x, y, z)
    private float camX = 0, camY = 0, camZ = 0;
    // 카메라 회전 (yaw: 좌우, pitch: 상하)
    private float yaw = 0, pitch = 0;

    // 키보드 입력 상태 (WASD)
    private boolean w, a, s, d;
    // 마우스 이전 위치
    private double lastMouseX = -1, lastMouseY = -1;

    // 1x1x1m 큐브 꼭짓점 (원점 중심 ±0.5)
    private final float[][] vertices = {
        {-0.5f, -0.5f, -0.5f}, {0.5f, -0.5f, -0.5f}, {0.5f, 0.5f, -0.5f}, {-0.5f, 0.5f, -0.5f},
        {-0.5f, -0.5f, 0.5f},  {0.5f, -0.5f, 0.5f},  {0.5f, 0.5f, 0.5f},  {-0.5f, 0.5f, 0.5f}
    };

    // 큐브의 12개 모서리
    private final int[][] edges = {
        {0,1}, {1,2}, {2,3}, {3,0}, // 뒷면
        {4,5}, {5,6}, {6,7}, {7,4}, // 앞면
        {0,4}, {1,5}, {2,6}, {3,7}  // 연결선
    };

    public void run() {
        init();
        loop();

        // 자원 해제 및 종료
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    private void init() {
        if (!glfwInit()) {
            throw new IllegalStateException("GLFW 초기화 실패");
        }

        // GLFW 윈도우 생성
        window = glfwCreateWindow(width, height, "LWJGL 3D Engine", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("GLFW 창 생성 실패");
        }

        // 키보드 입력 콜백 설정
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            boolean isPressed = (action != GLFW_RELEASE);
            if (key == GLFW_KEY_W) w = isPressed;
            if (key == GLFW_KEY_A) a = isPressed;
            if (key == GLFW_KEY_S) s = isPressed;
            if (key == GLFW_KEY_D) d = isPressed;
        });

        // 마우스 이동 콜백 설정
        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            if (lastMouseX != -1) {
                float dx = (float) (xpos - lastMouseX);
                float dy = (float) (ypos - lastMouseY);

                // 마우스 이동량 기반 시점 전환
                yaw += dx * 0.003f;
                pitch += dy * 0.003f; // OpenGL 좌표계에 맞춰 Pitch 방향 조정

                // Pitch 제한 (-90도 ~ 90도)
                float maxPitch = (float) (Math.PI / 2.1);
                if (pitch > maxPitch) pitch = maxPitch;
                if (pitch < -maxPitch) pitch = -maxPitch;
            }
            lastMouseX = xpos;
            lastMouseY = ypos;
        });

        // OpenGL 컨텍스트 생성 및 수직 동기화(v-sync) 설정
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1); // 60 FPS 고정 효과
        GL.createCapabilities();

        // 깊이 테스트 활성화
        glEnable(GL_DEPTH_TEST);
    }

    // WASD 카메라 이동 로직
    private void update() {
        float speed = 0.05f;
        // OpenGL은 진행 방향(전방)이 -Z축 방향
        if (w) { camX += Math.sin(yaw) * speed; camZ -= Math.cos(yaw) * speed; }
        if (s) { camX -= Math.sin(yaw) * speed; camZ += Math.cos(yaw) * speed; }
        if (a) { camX -= Math.cos(yaw) * speed; camZ -= Math.sin(yaw) * speed; }
        if (d) { camX += Math.cos(yaw) * speed; camZ += Math.sin(yaw) * speed; }
    }

    private void render() {
        // 화면 및 깊이 버퍼 초기화 (검은색 배경)
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        // 1. 투영 행렬 설정 (Perspective Projection)
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        
        float aspect = (float) width / height;
        float fov = 60.0f; // 시야각 (Degrees)
        float zNear = 0.1f;
        float zFar = 100.0f;
        
        // Perspective 절두체(Frustum) 계산
        float fh = (float) Math.tan(Math.toRadians(fov / 2.0)) * zNear;
        float fw = fh * aspect;
        glFrustum(-fw, fw, -fh, fh, zNear, zFar);

        // 2. 모델뷰 행렬 설정 (카메라 및 오브젝트 변환)
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        // 카메라 회전 및 이동 역변환 적용
        glRotatef((float) Math.toDegrees(pitch), 1.0f, 0.0f, 0.0f);
        glRotatef((float) Math.toDegrees(yaw), 0.0f, 1.0f, 0.0f);
        glTranslatef(-camX, -camY, -camZ);

        // 큐브를 카메라 앞 -3.0 (Z축) 위치에 배치
        glTranslatef(0.0f, 0.0f, -3.0f);

        // 3. 와이어프레임 렌더링 (녹색)
        glColor3f(0.0f, 1.0f, 0.0f);
        glBegin(GL_LINES);
        for (int[] edge : edges) {
            float[] v1 = vertices[edge[0]];
            float[] v2 = vertices[edge[1]];
            glVertex3f(v1[0], v1[1], v1[2]);
            glVertex3f(v2[0], v2[1], v2[2]);
        }
        glEnd();
    }

    // 메인 게임 루프
    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            update();
            render();

            glfwSwapBuffers(window); // 프론트/백 버퍼 교체
            glfwPollEvents();        // 키보드/마우스 이벤트 수집
        }
    }

    public static void main(String[] args) {
        new Engine3DLWJGL().run();
    }
}