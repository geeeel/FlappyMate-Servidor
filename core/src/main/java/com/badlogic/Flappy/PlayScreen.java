package com.badlogic.Flappy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

public class PlayScreen implements Screen {

    private final Main game;

    private OrthographicCamera camera;
    private Viewport viewport;
    private SpriteBatch batch;

    private Texture bg;
    private Texture ground;
    private Texture termoTexture;

    private Sound sfxJump;
    private Sound sfxHit;
    private boolean playedHit = false;

    private Mate mate;
    private Array<TermoPair> termos;

    private float termoTimer = 0f;
    private int score = 0;
    private boolean gameOver = false;

    private BitmapFont font;

    private float groundX1 = 0;
    private float groundX2;

    public PlayScreen(Main game) {
        this.game = game;
    }

    @Override
    public void show() {
        game.stopMenuMusic();

        camera = new OrthographicCamera();
        viewport = new FitViewport(Constants.VIRTUAL_WIDTH, Constants.VIRTUAL_HEIGHT, camera);
        batch = new SpriteBatch();

        bg = new Texture("bg.png");
        ground = new Texture("ground.png");
        termoTexture = new Texture("termo.png");

        mate = new Mate();
        termos = new Array<>();

        sfxJump = Gdx.audio.newSound(Gdx.files.internal("mate_sound.mp3"));
        sfxHit  = Gdx.audio.newSound(Gdx.files.internal("bruh.mp3"));

        groundX1 = 0;
        groundX2 = ground.getWidth();

        font = new BitmapFont();
        font.getData().setScale(2f);
    }

    private void spawnTermos() {
        float y = MathUtils.random(Constants.TERMO_MIN_Y, Constants.TERMO_MAX_Y);
        termos.add(new TermoPair(termoTexture, Constants.VIRTUAL_WIDTH + 40, y));
    }

    private void setGameOver() {
        if (!gameOver) {
            gameOver = true;
            if (!playedHit && Settings.soundEnabled) {
                sfxHit.play(0.9f);
                playedHit = true;
            }
        }
    }

    private void resetGame() {
        playedHit = false;
        termos.clear();
        mate.reset();
        score = 0;
        termoTimer = 0f;
        gameOver = false;
        groundX1 = 0;
        groundX2 = ground.getWidth();
    }

    private void update(float dt) {
        // Volver al menú
        if (Gdx.input.isKeyJustPressed(Input.Keys.M)) {
            game.setScreen(new MenuScreen(game));
            return;
        }

        // Saltar / Reiniciar
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.justTouched()) {
            if (gameOver) {
                resetGame();
            } else {
                mate.jump();
                if (Settings.soundEnabled) sfxJump.play(0.8f);
            }
        }

        // Si perdiste, no sigas actualizando el mundo
        if (gameOver) return;

        // Física del mate
        mate.update(dt);

        // Spawn termos
        termoTimer += dt;
        if (termoTimer >= Constants.TERMO_SPAWN_TIME) {
            termoTimer = 0f;
            spawnTermos();
        }

        // Update termos + score + remove
        for (int i = termos.size - 1; i >= 0; i--) {
            TermoPair t = termos.get(i);
            t.update(dt);

            if (t.shouldCountScore(mate.getX())) score++;
            if (t.isOffscreen()) termos.removeIndex(i);
        }

        // Ground scroll
        float dx = Constants.WORLD_SPEED * dt;
        groundX1 -= dx;
        groundX2 -= dx;
        if (groundX1 + ground.getWidth() < 0) groundX1 = groundX2 + ground.getWidth();
        if (groundX2 + ground.getWidth() < 0) groundX2 = groundX1 + ground.getWidth();

        // Colision: piso / techo
        if (mate.getY() <= Constants.GROUND_HEIGHT) setGameOver();
        if (mate.getY() + mate.getHeight() >= Constants.VIRTUAL_HEIGHT) setGameOver();

        // Collisions: termos
        for (TermoPair t : termos) {
            if (t.getBottomBounds().overlaps(mate.getBounds()) || t.getTopBounds().overlaps(mate.getBounds())) {
                setGameOver();
                break;
            }
        }
    }

    @Override
    public void render(float delta) {
        update(delta);

        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        // Fondo
        batch.draw(bg, 0, 0, Constants.VIRTUAL_WIDTH, Constants.VIRTUAL_HEIGHT);



        // Termos
        for (TermoPair t : termos) t.draw(batch);

        // Piso
        batch.draw(ground, groundX1, 0);
        batch.draw(ground, groundX2, 0);

        // Mate
        batch.draw(mate.getTexture(), mate.getX(), mate.getY());

        // HUD
        font.draw(batch, "Score: " + score, 20, Constants.VIRTUAL_HEIGHT - 20);

        if (gameOver) {
            font.draw(batch, "GAME OVER", 150, 450);
            font.draw(batch, "SPACE / Tap: restart", 90, 400);
            font.draw(batch, "M: volver al menu", 120, 350);
        }

        batch.end();
    }

    @Override public void resize(int width, int height) { viewport.update(width, height, true); }
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}

    @Override
    public void dispose() {
        if (batch != null) batch.dispose();
        if (sfxJump != null) sfxJump.dispose();
        if (sfxHit != null) sfxHit.dispose();
        if (bg != null) bg.dispose();
        if (ground != null) ground.dispose();
        if (termoTexture != null) termoTexture.dispose();
        if (mate != null) mate.dispose();
        if (font != null) font.dispose();
    }
}
