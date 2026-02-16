package red;

final class MateSim {
    private final float x;
    private final float w;
    private final float h;

    private float y;
    private float vy;

    private final AABB bounds;

    MateSim(float x, float startY, float w, float h) {
        this.x = x;
        this.y = startY;
        this.w = w;
        this.h = h;
        this.vy = 0f;
        this.bounds = new AABB(x, startY, w, h);
    }

    void update(float dt) {
        vy += com.badlogic.Flappy.Constants.GRAVITY * dt;
        y += vy * dt;
        bounds.set(x, y, w, h);
    }

    void jump() {
        vy = com.badlogic.Flappy.Constants.JUMP_VELOCITY;
    }

    AABB bounds() { return bounds; }
    float y() { return y; }
    float vy() { return vy; }
}
