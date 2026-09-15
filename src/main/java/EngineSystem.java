// 엔진 시스템 생명주기 계약
// - 부트스트랩(Engine3DLWJGL.init) 완료 후, 등록 순서대로 init() 1회 호출
// - 매 프레임: 등록 순서대로 update(dt) → render()
// - 종료 시: 등록 역순으로 dispose()
// - 필요한 단계만 오버라이드하면 됨 (default: 빈 구현)
public interface EngineSystem {
    default void init() {}
    default void update(float dt) {}
    default void render() {}
    default void dispose() {}
}
