package red;

import java.text.SimpleDateFormat;
import java.util.Date;

public final class LoggerRed {

    private static final SimpleDateFormat SDF = new SimpleDateFormat("HH:mm:ss.SSS");

    private LoggerRed() {}

    public static void info(String tag, String msg) {
        System.out.println(ts() + " [INFO] [" + tag + "] " + msg);
    }

    public static void warn(String tag, String msg) {
        System.out.println(ts() + " [WARN] [" + tag + "] " + msg);
    }

    public static void error(String tag, String msg) {
        System.err.println(ts() + " [ERR ] [" + tag + "] " + msg);
    }

    public static void error(String tag, String msg, Throwable t) {
        error(tag, msg);
        if (t != null) t.printStackTrace();
    }

    private static String ts() {
        return SDF.format(new Date());
    }
}
