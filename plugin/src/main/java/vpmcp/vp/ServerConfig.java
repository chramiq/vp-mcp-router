package vpmcp.vp;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Where the MCP endpoint listens. Defaults are fine for a single Visual Paradigm instance;
 * an {@code mcp.properties} next to plugin.xml moves the port when it is already taken.
 */
public final class ServerConfig {

    public static final String DEFAULT_BIND_ADDRESS = "127.0.0.1";
    public static final int DEFAULT_PORT = 8899;

    private static final String PROPERTIES_FILE = "mcp.properties";

    private final String bindAddress;
    private final int port;

    private ServerConfig(String bindAddress, int port) {
        this.bindAddress = bindAddress;
        this.port = port;
    }

    public static ServerConfig load(File pluginDir) {
        Properties properties = readProperties(pluginDir);
        String bindAddress = firstNonBlank(System.getProperty("vpmcp.bind"), properties.getProperty("bind"), DEFAULT_BIND_ADDRESS);
        int port = parsePort(firstNonBlank(System.getProperty("vpmcp.port"), properties.getProperty("port"), null));
        return new ServerConfig(bindAddress, port);
    }

    private static Properties readProperties(File pluginDir) {
        Properties properties = new Properties();
        if (pluginDir == null) {
            return properties;
        }
        File file = new File(pluginDir, PROPERTIES_FILE);
        if (!file.isFile()) {
            return properties;
        }
        try (InputStream stream = new FileInputStream(file)) {
            properties.load(stream);
        } catch (IOException unreadable) {
            VpLog.error("Could not read " + file.getAbsolutePath() + "; using defaults.", unreadable);
        }
        return properties;
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        if (second != null && !second.trim().isEmpty()) {
            return second.trim();
        }
        return fallback;
    }

    private static int parsePort(String value) {
        if (value == null) {
            return DEFAULT_PORT;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException notANumber) {
            VpLog.error("Port \"" + value + "\" is not a number; falling back to " + DEFAULT_PORT + ".", notANumber);
            return DEFAULT_PORT;
        }
    }

    public String getBindAddress() {
        return bindAddress;
    }

    public int getPort() {
        return port;
    }
}
