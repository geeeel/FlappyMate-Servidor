package red;

final class AABB {
    float x, y, w, h;

    AABB(float x, float y, float w, float h) {
        set(x, y, w, h);
    }

    void set(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    boolean overlaps(AABB o) {
        // Separating Axis Theorem para rectángulos axis-aligned
        return x < o.x + o.w &&
            x + w > o.x &&
            y < o.y + o.h &&
            y + h > o.y;
    }
}
