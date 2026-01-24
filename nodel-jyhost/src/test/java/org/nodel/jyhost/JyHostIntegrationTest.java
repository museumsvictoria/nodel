package org.nodel.jyhost;

import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

public class JyHostIntegrationTest {

    @Test
    public void scriptLoadsAndEventsFlow() throws Exception {
        Path root = Files.createTempDirectory("nodel-jyhost-it-");
        Path nodesRoot = root.resolve("nodes");
        Path emitterRoot = nodesRoot.resolve("Emitter");
        Path receiverRoot = nodesRoot.resolve("Receiver");
        Files.createDirectories(emitterRoot);
        Files.createDirectories(receiverRoot);

        writeFile(emitterRoot.resolve("script.py"),
                "from nodetoolkit import *\n" +
                "\n" +
                "local_event_Message = LocalEvent(\"Message\")\n" +
                "local_event_Tick = LocalEvent(\"Tick\")\n" +
                "\n" +
                "def local_action_NoArg():\n" +
                "    console.info(\"Emitter NoArg called\")\n" +
                "    local_event_Tick.emit()\n" +
                "\n" +
                "def local_action_Emit(arg):\n" +
                "    console.info(\"Emitter Emit called with %s\" % arg)\n" +
                "    local_event_Message.emit(arg)\n");

        writeFile(receiverRoot.resolve("script.py"),
                "from nodetoolkit import *\n" +
                "\n" +
                "def remote_event_Message(arg):\n" +
                "    console.info(\"Receiver Message %s\" % arg)\n" +
                "\n" +
                "def remote_event_Tick():\n" +
                "    console.info(\"Receiver Tick\")\n");

        writeFile(receiverRoot.resolve("nodeConfig.json"),
                "{\n" +
                "  \"remoteBindingValues\": {\n" +
                "    \"actions\": {},\n" +
                "    \"events\": {\n" +
                "      \"Message\": {\"node\": \"Emitter\", \"event\": \"Message\"},\n" +
                "      \"Tick\": {\"node\": \"Emitter\", \"event\": \"Tick\"}\n" +
                "    }\n" +
                "  },\n" +
                "  \"paramValues\": {}\n" +
                "}\n");

        Process process = startHost(root, nodesRoot);
        StringBuilder output = new StringBuilder();
        Thread gobbler = startGobbler(process.getInputStream(), output);

        try {
            int port = waitForPort(root.resolve(".lastHTTPPort"), process, output, 30000);
            String baseUrl = "http://127.0.0.1:" + port;

            waitForContains(baseUrl + "/REST/nodes", "\"Emitter\"", process, output, 30000);
            waitForContains(baseUrl + "/REST/nodes", "\"Receiver\"", process, output, 30000);
            waitForContains(baseUrl + "/REST/nodes/Emitter/actions", "\"NoArg\"", process, output, 30000);
            waitForContains(baseUrl + "/REST/nodes/Emitter/actions", "\"Emit\"", process, output, 30000);

            httpGet(baseUrl + "/REST/nodes/Emitter/actions/NoArg/call");
            httpGet(baseUrl + "/REST/nodes/Emitter/actions/Emit/call?arg=hello");

            waitForContains(baseUrl + "/REST/nodes/Receiver/console?from=0&max=200", "Receiver Tick", process, output, 30000);
            waitForContains(baseUrl + "/REST/nodes/Receiver/console?from=0&max=200", "Receiver Message hello", process, output, 30000);
        } finally {
            process.destroy();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            gobbler.join(5000);
            deleteTree(root);
        }
    }

    private static Process startHost(Path root, Path nodesRoot) throws IOException {
        String javaExec = resolveJavaExecutable();
        String classpath = System.getProperty("java.class.path");

        ProcessBuilder builder = new ProcessBuilder(
                javaExec,
                "-cp", classpath,
                "org.nodel.jyhost.Launch",
                "-p", "0",
                "-r", nodesRoot.toString(),
                "--disableAdvertisements"
        );
        builder.directory(root.toFile());
        builder.redirectErrorStream(true);
        return builder.start();
    }

    private static String resolveJavaExecutable() {
        String javaHome = System.getProperty("java.home");
        String bin = isWindows() ? "java.exe" : "java";
        Path javaPath = Paths.get(javaHome, "bin", bin);
        if (Files.isExecutable(javaPath)) {
            return javaPath.toString();
        }
        return "java";
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().contains("win");
    }

    private static Thread startGobbler(InputStream inputStream, StringBuilder output) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (output) {
                        if (output.length() < 20000) {
                            output.append(line).append('\n');
                        }
                    }
                }
            } catch (IOException ignore) {
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static int waitForPort(Path portFile, Process process, StringBuilder output, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive()) {
                Assert.fail("Host process exited early.\n" + output);
            }
            if (Files.exists(portFile)) {
                String portText = new String(Files.readAllBytes(portFile), StandardCharsets.UTF_8).trim();
                if (!portText.isEmpty()) {
                    return Integer.parseInt(portText);
                }
            }
            Thread.sleep(200);
        }
        Assert.fail("Timed out waiting for host port file.\n" + output);
        return -1;
    }

    private static void waitForContains(String url, String token, Process process, StringBuilder output, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String lastBody = null;
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive()) {
                Assert.fail("Host process exited early.\n" + output);
            }
            try {
                lastBody = httpGet(url);
                if (lastBody.contains(token)) {
                    return;
                }
            } catch (IOException ignore) {
            }
            Thread.sleep(250);
        }
        Assert.fail("Timed out waiting for '" + token + "' in " + url + ". Last response:\n" + lastBody);
    }

    private static String httpGet(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(2000);

        int code = connection.getResponseCode();
        InputStream stream = (code >= 200 && code < 400) ? connection.getInputStream() : connection.getErrorStream();
        if (stream == null) {
            return "";
        }

        try (InputStream inputStream = stream) {
            return readAll(inputStream);
        } finally {
            connection.disconnect();
        }
    }

    private static String readAll(InputStream inputStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = inputStream.read(buffer)) > 0) {
            baos.write(buffer, 0, count);
        }
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void writeFile(Path path, String content) throws IOException {
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void deleteTree(Path root) {
        try {
            Files.walk(root)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignore) {
                        }
                    });
        } catch (IOException ignore) {
        }
    }
}