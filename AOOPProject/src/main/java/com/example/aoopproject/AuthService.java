package com.example.aoopproject;

import com.example.aoopproject.LineClient;

public class AuthService {
    private final LineClient client;
    public record Result(boolean ok, String message) {}

    public AuthService(LineClient client) { this.client = client; }

    public Result login(String username, String password) {
        return send("LOGIN: " + sanitize(username) + ", " + sanitize(password));
    }

    // (You’ll reuse this for your signup screen later)
    public Result signup(String username, String email, String password) {
        return send("SIGNUP: " + sanitize(username) + ", " + sanitize(email) + ", " + sanitize(password));
    }

    private Result send(String line) {
        try {
            String resp = client.request(line);
            boolean ok = resp.startsWith("SUCCESS:");
            String msg = resp.replaceFirst("^(SUCCESS:|ERROR:)\\s*", "");
            return new Result(ok, msg);
        } catch (Exception e) {
            return new Result(false, "Network error: " + e.getMessage());
        }
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        return s.replace(",", " ").replace("\n", " ").replace("\r", " ").trim();
    }
}
