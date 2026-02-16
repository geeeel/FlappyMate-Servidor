package red;

import com.badlogic.Flappy.Constants;

final class TermoSim {
    float x;
    final float gapCenterY;

    final AABB bottom;
    final AABB top;

    private boolean passedP1 = false;
    private boolean passedP2 = false;

    TermoSim(float startX, float gapCenterY, float termoW) {
        this.x = startX;
        this.gapCenterY = gapCenterY;

        float bottomTopY = gapCenterY - Constants.TERMO_GAP / 2f;
        float topBottomY  = gapCenterY + Constants.TERMO_GAP / 2f;

        bottom = new AABB(x, Constants.GROUND_HEIGHT, termoW, bottomTopY - Constants.GROUND_HEIGHT);
        top    = new AABB(x, topBottomY, termoW, Constants.VIRTUAL_HEIGHT - topBottomY);
    }

    void update(float dt) {
        x -= Constants.WORLD_SPEED * dt;
        bottom.x = x;
        top.x = x;
    }

    boolean isOffscreen(float termoW) {
        return x + termoW < 0;
    }

    boolean tryScore(int playerId, float mateX, float termoW) {
        boolean passed = mateX > x + termoW;
        if (!passed) return false;

        if (playerId == 1 && !passedP1) { passedP1 = true; return true; }
        if (playerId == 2 && !passedP2) { passedP2 = true; return true; }

        return false;
    }
}
