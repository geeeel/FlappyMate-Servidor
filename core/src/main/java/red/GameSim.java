package red;

import com.badlogic.Flappy.Constants;
import red.TermoSim;

import java.util.ArrayList;
import java.util.Random;

final class GameSim {

    // Lobby ready indexado por id (1..2). index 0 sin uso.
    final boolean[] ready = new boolean[3];

    // Mundo / termos
    private final Random rng = new Random();
    private final ArrayList<TermoSim> termos = new ArrayList<>();

    // Parámetros de colisión: necesito ancho/alto del mate y ancho del termo.
    // Como servidor no carga texturas, usted debe setear estos valores coherentes con los assets.
    // Valores razonables por defecto (se ajustan si su mate.png/termo.png difieren).
    private float mateW = 48f;
    private float mateH = 48f;
    private float termoW = 80f;

    // Mates
    private MateSim p1;
    private MateSim p2;

    private boolean p1Alive = true;
    private boolean p2Alive = true;

    int score1 = 0;
    int score2 = 0;

    // Timers
    private float termoTimer = 0f;

    // Input queued (para aplicar 1 salto por tick si llegó)
    private boolean jumpP1 = false;
    private boolean jumpP2 = false;

    // X fijo del mate (en su cliente es 120)
    private static final float MATE_X = 120f;

    void resetForNewMatch() {
        termos.clear();
        termoTimer = 0f;

        score1 = 0;
        score2 = 0;

        p1Alive = true;
        p2Alive = true;

        // Reset mates al centro
        float startY = Constants.VIRTUAL_HEIGHT / 2f;
        p1 = new MateSim(MATE_X, startY, mateW, mateH);
        p2 = new MateSim(MATE_X, startY, mateW, mateH);

        jumpP1 = false;
        jumpP2 = false;

        // Spawn inicial de 2 termos para no arrancar vacío
        spawnTermo();
        spawnTermo();
    }

    void resetLobbyAfterAbort() {
        // No reseteo ready si usted quiere que se mantenga. Pero usted pidió lobby simple
        // y abort con error; yo lo pongo todo en false para que el usuario vuelva a confirmar.
        ready[1] = false;
        ready[2] = false;
    }

    boolean isAlive(int id) {
        return id == 1 ? p1Alive : p2Alive;
    }

    void queueJump(int id) {
        if (id == 1) jumpP1 = true;
        if (id == 2) jumpP2 = true;
    }

    void tick(float dt) {
        // Aplicar saltos en el borde del tick
        if (p1Alive && jumpP1) p1.jump();
        if (p2Alive && jumpP2) p2.jump();
        jumpP1 = false;
        jumpP2 = false;

        // Si están muertos, no actualizo su física (queda congelado)
        if (p1Alive) p1.update(dt);
        if (p2Alive) p2.update(dt);

        // Spawn termos
        termoTimer += dt;
        if (termoTimer >= Constants.TERMO_SPAWN_TIME) {
            termoTimer = 0f;
            spawnTermo();
        }

        // Mover termos
        for (int i = termos.size() - 1; i >= 0; i--) {
            TermoSim t = termos.get(i);
            t.update(dt);

            // Score por jugador, solo si vivo
            if (p1Alive && t.tryScore(1, MATE_X, termoW)) score1++;
            if (p2Alive && t.tryScore(2, MATE_X, termoW)) score2++;

            if (t.isOffscreen(termoW)) termos.remove(i);
        }

        // Colisiones: suelo/techo
        if (p1Alive) {
            if (p1.y() <= Constants.GROUND_HEIGHT) p1Alive = false;
            if (p1.y() + mateH >= Constants.VIRTUAL_HEIGHT) p1Alive = false;
        }
        if (p2Alive) {
            if (p2.y() <= Constants.GROUND_HEIGHT) p2Alive = false;
            if (p2.y() + mateH >= Constants.VIRTUAL_HEIGHT) p2Alive = false;
        }

        // Colisiones: termos
        for (int i = 0; i < termos.size(); i++) {
            TermoSim t = termos.get(i);

            if (p1Alive && (t.bottom.overlaps(p1.bounds()) || t.top.overlaps(p1.bounds()))) {
                p1Alive = false;
            }
            if (p2Alive && (t.bottom.overlaps(p2.bounds()) || t.top.overlaps(p2.bounds()))) {
                p2Alive = false;
            }

            // Si ambos murieron, sigo moviendo el mundo igualmente?
            // Usted pidió que si uno muere el otro siga. Si ambos mueren, la partida queda "muerta".
            // Yo la dejo viva (termos siguen), pero no hay más score. Si no le gusta, lo cortamos.
        }
    }

    private void spawnTermo() {
        float gapY = Constants.TERMO_MIN_Y + rng.nextFloat() * (Constants.TERMO_MAX_Y - Constants.TERMO_MIN_Y);
        float startX = Constants.VIRTUAL_WIDTH + 40f;
        termos.add(new TermoSim(startX, gapY, termoW));
    }

    String buildState(int tick) {
        // STATE;tick=10;P1=y,vy,alive,score;P2=y,vy,alive,score;T=x,gap|x,gap|...;
        StringBuilder sb = new StringBuilder(512);

        sb.append("STATE;tick=").append(tick).append(";");

        sb.append("P1=")
            .append(fmt(p1.y())).append(",")
            .append(fmt(p1.vy())).append(",")
            .append(p1Alive ? 1 : 0).append(",")
            .append(score1).append(";");

        sb.append("P2=")
            .append(fmt(p2.y())).append(",")
            .append(fmt(p2.vy())).append(",")
            .append(p2Alive ? 1 : 0).append(",")
            .append(score2).append(";");

        sb.append("T=");
        for (int i = 0; i < termos.size(); i++) {
            TermoSim t = termos.get(i);
            sb.append(fmt(t.x)).append(",").append(fmt(t.gapCenterY));
            if (i < termos.size() - 1) sb.append("|");
        }
        sb.append(";");

        return sb.toString();
    }

    // Formateo más corto para no mandar floats kilométricos
    private static String fmt(float v) {
        return String.format(java.util.Locale.US, "%.2f", v);
    }
}
