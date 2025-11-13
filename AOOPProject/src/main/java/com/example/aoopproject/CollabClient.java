package com.example.aoopproject;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class CollabClient {
    private final String host;
    private final int port;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Thread readerThread;
    private Consumer<String> onMessage;

    public CollabClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void setOnMessage(Consumer<String> onMessage) {
        this.onMessage = onMessage;
    }

    public void connect(String username, String room) throws IOException {
        socket = new Socket(host, port);
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        in  = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

        out.println("HELLO " + username + " " + room);
        String ok = in.readLine();
        if (!"OK".equals(ok)) throw new IOException("Server refused: " + ok);

        readerThread = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    if (onMessage != null) onMessage.accept(line);
                }
            } catch (IOException ignored) {}
        }, "collab-client-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public void sendDraw(String phase, double x, double y, String colorHex, double width) {
        out.println("DRAW " + phase + " " + x + " " + y + " " + colorHex + " " + width);
    }

    public void sendChat(String message) {
        if (message == null) return;
        out.println("CHAT " + message);
    }

    public void leave() {
        out.println("LEAVE");
        close();
    }

    public void close() {
        try { if (out != null) out.flush(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
    }
}
