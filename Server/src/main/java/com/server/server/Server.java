package com.server.server;

import java.io.*;
import java.net.*;

public class Server {

    public static void main(String[] args) {
        final int PORT = 6000;

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started. Listening on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected.");

                new Thread(() -> handleClient(clientSocket)).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket socket) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String line = in.readLine();

            if (line == null || line.isEmpty()) {
                out.println("ERROR: No data received");
                return;
            }

            System.out.println("Received: " + line);

            if (line.startsWith("SIGNUP:")) {
                handleSignup(line.substring(7).trim(), out);
            } else if (line.startsWith("LOGIN:")) {
                handleLogin(line.substring(6).trim(), out);
            } else if (line.equals("GET_USERS")) {
                handleGetUsers(out);
            } else if (line.equals("GET_LOGS")) {
                handleGetLogs(out);
            } else if (line.startsWith("BAN:")) {
                handleBanUser(line.substring(4).trim(), out);
            } else if (line.startsWith("RESET:")) {
                handleResetUser(line.substring(6).trim(), out);
            } else {
                out.println("ERROR: Unknown request type");
            }

        } catch (IOException e) {
            System.err.println("Client handling error: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    private static void handleSignup(String data, PrintWriter out) {
        String[] parts = data.split(",");
        if (parts.length < 3) {
            out.println("ERROR: Invalid signup format");
            DatabaseHelper.logEvent("SIGNUP_FAIL", "Invalid format: " + data);
            return;
        }

        String username = parts[0].trim();
        String email = parts[1].trim();
        String password = parts[2].trim();

        boolean success = DatabaseHelper.registerUser(username, email, password);

        if (success) {
            out.println("SUCCESS: Signup completed");
        } else {
            out.println("ERROR: Username already exists");
        }
    }

    private static void handleLogin(String data, PrintWriter out) {
        String[] parts = data.split(",");
        if (parts.length < 2) {
            out.println("ERROR: Invalid login format");
            DatabaseHelper.logEvent("LOGIN_FAIL", "Invalid format: " + data);
            return;
        }

        String username = parts[0].trim();
        String password = parts[1].trim();

        boolean valid = DatabaseHelper.validateLogin(username, password);

        if (valid) {
            out.println("SUCCESS: Login successful");
        } else {
            out.println("ERROR: Invalid username or password");
        }
    }

    private static void handleGetUsers(PrintWriter out) {
        String result = DatabaseHelper.getAllUsers();
        out.println(result);
    }

    private static void handleGetLogs(PrintWriter out) {
        String logs = DatabaseHelper.getLogs();
        out.println(logs);
    }

    private static void handleBanUser(String username, PrintWriter out) {
        boolean success = DatabaseHelper.banUser(username);
        if (success) {
            out.println("SUCCESS: User banned");
        } else {
            out.println("ERROR: Failed to ban user");
        }
    }

    private static void handleResetUser(String username, PrintWriter out) {
        boolean success = DatabaseHelper.resetUser(username);
        if (success) {
            out.println("SUCCESS: User reset");
        } else {
            out.println("ERROR: Failed to reset user");
        }
    }
}
