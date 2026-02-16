package red;

public final class ServerLauncher {

    public static void main(String[] args) {
        int port = 4321;

        // Si quiere: java ... ServerLauncher 5555
        if (args != null && args.length >= 1) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (Exception ignored) {
                System.out.println("[SERVER] Puerto inválido en args, usando 4321.");
            }
        }

        LoggerRed.info("BOOT", "Iniciando servidor UDP en puerto " + port);

        HiloServidorFlappy server = new HiloServidorFlappy(port);
        server.start();

        // Shutdown hook para cierre prolijo
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LoggerRed.warn("BOOT", "Shutdown hook: apagando servidor...");
            server.apagarServidor("shutdown_hook");
        }));
    }
}
