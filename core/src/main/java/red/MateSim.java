package red;

final class MateSim {
    private final float x;
    private final float w;
    private final float h;

    // padding: recorta la hitbox por todos los lados
    private final float pad;

    private float y;
    private float vy;

    private final AABB bounds;

    MateSim(float x, float startY, float w, float h, float pad) {
        this.x = x;
        this.y = startY;
        this.w = w;
        this.h = h;
        this.pad = Math.max(0f, pad);
        this.vy = 0f;

        // bounds inicia ya recortado
        this.bounds = new AABB(x + this.pad, startY + this.pad, w - 2f * this.pad, h - 2f * this.pad);
    }

    void update(float dt) {
        vy += com.badlogic.Flappy.Constants.GRAVITY * dt;
        y += vy * dt;

        // bounds siempre recortado
        bounds.set(x + pad, y + pad, w - 2f * pad, h - 2f * pad);
    }

    void jump() {
        vy = com.badlogic.Flappy.Constants.JUMP_VELOCITY;
    }

    AABB bounds() { return bounds; }
    float y() { return y; }
    float vy() { return vy; }
    float x() { return x; }
}
