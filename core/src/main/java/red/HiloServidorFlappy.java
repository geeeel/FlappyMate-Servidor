package red;

import com.badlogic.Flappy.Constants;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class HiloServidorFlappy extends Thread {

    // =========================
    // Configuración
    // =========================
    private final int puerto;
    private DatagramSocket socket;

    private static final long TIMEOUT_MS = 5_000L;
    private static final long TICK_NS = 16_666_666L; // ~60fps

    // Protocolo
    private static final String MSG_HANDSHAKE_IN = "Hello_There";
    private static final String MSG_HANDSHAKE_OUT = "General_Kenobi";

    private static final String MSG_CONECTAR = "Conectar";
    private static final String MSG_CONECTADO = "Conectado";
    private static final String MSG_NO_REGISTRADO = "No_registrado";

    private static final String MSG_PING = "PING";
    private static final String MSG_PONG = "PONG";
    private static final String MSG_DISCONNECT = "DISCONNECT";

    private static final String MSG_PARTIDA_INICIADA = "PARTIDA_INICIADA";
    private static final String MSG_PARTIDA_ABORTADA = "PARTIDA_ABORTADA";

    // Lobby
    private static final String PREFIX_READY = "READY="; // READY=1/0

    // Input
    private static final String PREFIX_INPUT = "INPUT;"; // INPUT;jump=1;seq=123

    // Errores server->client
    private static final String PREFIX_SERVER_ERROR = "SERVER_ERROR;";

    // =========================
    // Estado de clientes
    // =========================
    private final Object lock = new Object();
    private volatile boolean activo = true;

    private final Map<Integer, Cliente> clientes = new ConcurrentHashMap<>();

    private volatile boolean partidaActiva = false;
    private Thread hiloSim = null;

    private final GameSim sim = new GameSim();

    public HiloServidorFlappy(int puerto) {
        super("HiloServidorFlappy-UDP");
        this.puerto = puerto;
        initSocket();
        startTimeoutCleaner();
    }

    private void initSocket() {
        try {
            socket = new DatagramSocket(puerto);
            socket.setBroadcast(true);
            LoggerRed.info("SERVER", "Escuchando UDP en " + puerto);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo abrir socket UDP en puerto " + puerto, e);
        }
    }

    public void apagarServidor(String reason) {
        LoggerRed.warn("SERVER", "Apagando servidor. reason=" + reason);
        activo = false;
        detenerSimulacion();
        if (socket != null && !socket.isClosed()) socket.close();
    }

    @Override
    public void run() {
        while (activo) {
            try {
                byte[] buf = new byte[1400];
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                socket.receive(p);
                procesarPaquete(p);
            } catch (SocketException se) {
                if (!activo) break;
                LoggerRed.error("SOCKET", "SocketException en receive()", se);
            } catch (Exception e) {
                LoggerRed.error("SERVER", "Excepción en loop principal", e);
            }
        }
    }

    private void procesarPaquete(DatagramPacket p) {
        String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8).trim();

        // DEBUG: log de TODO lo que llega (temporal)
        LoggerRed.info("RECV", "De " + p.getAddress().getHostAddress() + ":" + p.getPort() + " msg=" + msg);

        // 1) Handshake discovery
        if (MSG_HANDSHAKE_IN.equals(msg)) {
            enviar(MSG_HANDSHAKE_OUT, p.getAddress(), p.getPort());
            return;
        }

        // 2) Conectar (registro)
        if (MSG_CONECTAR.equals(msg)) {
            registrarCliente(p);
            return;
        }

        // 3) Mensajes solo si está registrado
        Cliente c = getClienteByPacket(p);
        if (c == null) {
            enviar(MSG_NO_REGISTRADO, p.getAddress(), p.getPort());
            return;
        }

        c.ultimoMsgMs = System.currentTimeMillis();

        // 4) Routing simple
        if (MSG_PING.equals(msg)) {
            enviar(MSG_PONG, c.ip, c.puerto);
            return;
        }

        if (MSG_DISCONNECT.equals(msg)) {
            manejarDesconexion(c, "client_disconnect");
            return;
        }

        if (msg.startsWith(PREFIX_READY)) {
            manejarReady(c, msg);
            return;
        }

        if (msg.startsWith(PREFIX_INPUT)) {
            LoggerRed.info("INPUT", "De P" + c.id + " msg=" + msg + " partidaActiva=" + partidaActiva + " alive=" + sim.isAlive(c.id));
            manejarInput(c, msg);
            return;
        }


        if (msg.startsWith(PREFIX_INPUT)) {
            manejarInput(c, msg);
            return;
        }

        // Mensaje desconocido (lo logueo para debug)
        LoggerRed.warn("MSG", "Desconocido de P" + c.id + ": " + msg);
    }

    // =========================
    // Registro / Clientes
    // =========================
    private void registrarCliente(DatagramPacket p) {
        synchronized (lock) {
            // Ya estaba registrado por IP:PUERTO
            Cliente ya = getClienteByPacket(p);
            if (ya != null) return;

            int id = obtenerIdDisponible();
            if (id == -1) {
                // Server full
                enviar(PREFIX_SERVER_ERROR + "code=FULL;detail=server_full", p.getAddress(), p.getPort());
                return;
            }

            Cliente c = new Cliente(id, p.getAddress(), p.getPort());
            clientes.put(id, c);

            enviar(MSG_CONECTADO, c.ip, c.puerto);
            enviar("Registrado con ID " + id, c.ip, c.puerto);

            LoggerRed.info("JOIN", "Cliente registrado: P" + id + " " + c.ip + ":" + c.puerto);

            // Sync lobby inicial
            broadcastLobbyState();

            // Si ya había partida activa, no lo dejo entrar a medio juego (para evitar líos)
            if (partidaActiva) {
                enviar(PREFIX_SERVER_ERROR + "code=IN_GAME;detail=match_in_progress", c.ip, c.puerto);
            }
        }
    }

    private int obtenerIdDisponible() {
        boolean p1 = clientes.containsKey(1);
        boolean p2 = clientes.containsKey(2);
        if (!p1) return 1;
        if (!p2) return 2;
        return -1;
    }

    private Cliente getClienteByPacket(DatagramPacket p) {
        for (Cliente c : clientes.values()) {
            if (c.ip.equals(p.getAddress()) && c.puerto == p.getPort()) return c;
        }
        return null;
    }

    private void manejarDesconexion(Cliente c, String reason) {
        synchronized (lock) {
            LoggerRed.warn("LEAVE", "Desconexión: P" + c.id + " reason=" + reason);
            clientes.remove(c.id);
            sim.ready[c.id] = false;

            // aborta si estábamos en partida y queda <2
            if (partidaActiva) {
                abortarPartida("player_left_" + c.id);
            } else {
                broadcastLobbyState();
            }
        }
    }

    // =========================
    // Lobby
    // =========================
    private void manejarReady(Cliente c, String msg) {
        // READY=1 o READY=0
        boolean val = msg.endsWith("1");
        sim.ready[c.id] = val;

        LoggerRed.info("LOBBY", "P" + c.id + " READY=" + (val ? "1" : "0"));

        broadcastLobbyState();

        // Condición de inicio
        boolean ambos = clientes.containsKey(1) && clientes.containsKey(2) && sim.ready[1] && sim.ready[2];
        if (ambos && !partidaActiva) {
            iniciarPartida();
        }
    }

    private void broadcastLobbyState() {
        // LOBBY;READY_P1=0;READY_P2=1
        String m = "LOBBY;READY_P1=" + (sim.ready[1] ? 1 : 0) + ";READY_P2=" + (sim.ready[2] ? 1 : 0);
        broadcast(m);
    }

    // =========================
    // Input
    // =========================
    private void manejarInput(Cliente c, String msg) {
        // INPUT;jump=1;seq=123
        // Solo me importa jump=1, y solo si ese jugador está vivo
        if (!partidaActiva) return;

        if (!sim.isAlive(c.id)) return;

        boolean jump = msg.contains("jump=1");
        if (jump) {
            sim.queueJump(c.id);
        }
    }

    // =========================
    // Partida: start/abort/sim
    // =========================
    private void iniciarPartida() {
        if (partidaActiva) return;

        LoggerRed.info("GAME", "Iniciando partida.");

        // Reset sim
        sim.resetForNewMatch();

        partidaActiva = true;

        broadcast(MSG_PARTIDA_INICIADA);

        iniciarSimulacion();
    }

    private void abortarPartida(String reason) {
        LoggerRed.warn("GAME", "Abortando partida. reason=" + reason);

        detenerSimulacion();
        partidaActiva = false;

        // Reset lobby state
        sim.resetLobbyAfterAbort();

        broadcast(PREFIX_SERVER_ERROR + "code=ABORT;detail=" + reason);
        broadcast(MSG_PARTIDA_ABORTADA);
        broadcastLobbyState();
    }

    private void detenerSimulacion() {
        if (hiloSim != null) {
            try {
                hiloSim.interrupt();
            } catch (Exception ignored) {}
            hiloSim = null;
        }
    }

    private void iniciarSimulacion() {
        hiloSim = new Thread(() -> {
            long last = System.nanoTime();
            long accSpawnMs = 0L;
            long tick = 0;

            // Para dt estable: aunque haya jitter, clamp
            while (partidaActiva && activo) {
                long now = System.nanoTime();
                long dtNs = now - last;
                last = now;

                // dt en segundos
                float dt = dtNs / 1_000_000_000f;
                if (dt > 0.05f) dt = 0.05f; // clamp anti-lag

                // Update sim
                sim.tick(dt);

                // Estado a clientes
                String state = sim.buildState((int) tick);
                broadcast(state);

                tick++;

                // Sleep para ~60 fps
                long spent = System.nanoTime() - now;
                long sleepNs = TICK_NS - spent;
                if (sleepNs > 0) {
                    try {
                        Thread.sleep(sleepNs / 1_000_000L);
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }

            LoggerRed.info("SIM", "Hilo simulación terminado.");
        }, "ServidorFlappy-Sim");

        hiloSim.start();
    }

    // =========================
    // Timeout cleaner
    // =========================
    private void startTimeoutCleaner() {
        Thread cleaner = new Thread(() -> {
            while (activo) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                    break;
                }

                long now = System.currentTimeMillis();
                List<Cliente> caidos = new ArrayList<>();

                for (Cliente c : clientes.values()) {
                    if ((now - c.ultimoMsgMs) > TIMEOUT_MS) {
                        caidos.add(c);
                    }
                }

                if (!caidos.isEmpty()) {
                    synchronized (lock) {
                        for (Cliente c : caidos) {
                            LoggerRed.warn("TIMEOUT", "P" + c.id + " timeout (" + TIMEOUT_MS + "ms)");
                            clientes.remove(c.id);
                            sim.ready[c.id] = false;
                        }

                        if (partidaActiva) {
                            abortarPartida("timeout");
                        } else {
                            broadcastLobbyState();
                        }
                    }
                }
            }
        }, "ServidorFlappy-Cleaner");

        cleaner.setDaemon(true);
        cleaner.start();
    }

    // =========================
    // UDP send helpers
    // =========================
    private void enviar(String msg, InetAddress ip, int port) {
        try {
            byte[] data = msg.getBytes(StandardCharsets.UTF_8);
            DatagramPacket p = new DatagramPacket(data, data.length, ip, port);
            socket.send(p);
        } catch (IOException ioe) {
            // no tumbar server por un send fallido
            LoggerRed.warn("SEND", "Fallo envío a " + ip + ":" + port + " msg=" + msg);
        }
    }

    private void broadcast(String msg) {
        for (Cliente c : clientes.values()) {
            enviar(msg, c.ip, c.puerto);
        }
    }

    // =========================
    // Cliente struct
    // =========================
    private static final class Cliente {
        final int id;
        final InetAddress ip;
        final int puerto;
        volatile long ultimoMsgMs;

        Cliente(int id, InetAddress ip, int puerto) {
            this.id = id;
            this.ip = ip;
            this.puerto = puerto;
            this.ultimoMsgMs = System.currentTimeMillis();
        }
    }
}
