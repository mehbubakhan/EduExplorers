package com.example.aoopproject;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class LineClient {
    private final String host;
    private final int port;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public LineClient(String host, int port) {
        this(host, port, 3000, 5000);
    }

    public LineClient(String host, int port, int connectTimeoutMs, int readTimeoutMs) {
        this.host = host;
        this.port = port;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    /** Sends one request line and returns one response line. */
    public String request(String line) throws IOException {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            s.setSoTimeout(readTimeoutMs);

            try (var out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
                 var in  = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
                out.println(line);
                String response = in.readLine();
                if (response == null) throw new EOFException("Server closed connection");
                return response;
            }
        }
    }
}
