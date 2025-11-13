package com.server.server;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Text-file "database".
 * users.txt  -> username,email,salt,hash,status
 * logs.txt   -> ISO_TIME,event,details
 */
public class DatabaseHelper {

    private static final Path DATA_DIR   = Paths.get("data");
    private static final Path USERS_FILE = DATA_DIR.resolve("users.txt");
    private static final Path LOGS_FILE  = DATA_DIR.resolve("logs.txt");

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_BANNED = "BANNED";

    // In-memory cache for speed; persisted on every change.
    // username -> record
    private static final Map<String, UserRecord> USERS = new LinkedHashMap<>();

    static {
        initFiles();
        loadUsers();
    }

    // ---------- Public API used by Server.java ----------

    public static synchronized boolean registerUser(String username, String email, String password) {
        if (username.isBlank() || email.isBlank() || password.isBlank()) {
            logEvent("SIGNUP_FAIL", "Empty field(s) for username=" + username);
            return false;
        }
        if (USERS.containsKey(username)) {
            logEvent("SIGNUP_FAIL", "User exists: " + username);
            return false;
        }
        String salt = randomSalt();
        String hash = hashPassword(password, salt);
        UserRecord rec = new UserRecord(username, email, salt, hash, STATUS_ACTIVE);
        USERS.put(username, rec);
        persistUsers();
        logEvent("SIGNUP_OK", "username=" + username + ", email=" + email);
        return true;
    }

    public static synchronized boolean validateLogin(String username, String password) {
        UserRecord rec = USERS.get(username);
        if (rec == null) {
            logEvent("LOGIN_FAIL", "no_such_user: " + username);
            return false;
        }
        if (STATUS_BANNED.equals(rec.status)) {
            logEvent("LOGIN_FAIL", "banned_user: " + username);
            return false;
        }
        boolean ok = slowEquals(hashPassword(password, rec.salt), rec.hash);
        logEvent(ok ? "LOGIN_OK" : "LOGIN_FAIL", "username=" + username);
        return ok;
    }

    public static synchronized String getAllUsers() {
        // Return as newline-separated CSV header + rows
        StringBuilder sb = new StringBuilder("username,email,status");
        for (UserRecord r : USERS.values()) {
            sb.append("\n").append(r.username).append(",").append(r.email).append(",").append(r.status);
        }
        return sb.toString();
    }

    public static synchronized String getLogs() {
        try {
            if (Files.notExists(LOGS_FILE)) return "";
            return Files.readString(LOGS_FILE, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "ERROR: cannot read logs";
        }
    }

    public static synchronized boolean banUser(String username) {
        UserRecord rec = USERS.get(username);
        if (rec == null) return false;
        if (STATUS_BANNED.equals(rec.status)) return true;
        rec.status = STATUS_BANNED;
        persistUsers();
        logEvent("BAN", "username=" + username);
        return true;
    }

    public static synchronized boolean resetUser(String username) {
        // sets status ACTIVE and resets password to a random temporary password (returned in logs)
        UserRecord rec = USERS.get(username);
        if (rec == null) return false;
        rec.status = STATUS_ACTIVE;
        String temp = genTempPassword();
        rec.salt = randomSalt();
        rec.hash = hashPassword(temp, rec.salt);
        persistUsers();
        logEvent("RESET", "username=" + username + ", temp_password=" + temp);
        return true;
    }

    public static synchronized void logEvent(String event, String details) {
        try {
            String ts = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            Files.createDirectories(DATA_DIR);
            Files.writeString(
                    LOGS_FILE,
                    ts + "," + event + "," + details + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    Files.exists(LOGS_FILE) ? StandardOpenOption.APPEND : StandardOpenOption.CREATE
            );
        } catch (IOException ignored) {}
    }

    // ---------- Internals ----------

    private static void initFiles() {
        try {
            Files.createDirectories(DATA_DIR);
            if (Files.notExists(USERS_FILE)) Files.createFile(USERS_FILE);
            if (Files.notExists(LOGS_FILE)) Files.createFile(LOGS_FILE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to init data folder", e);
        }
    }

    private static void loadUsers() {
        try (BufferedReader br = Files.newBufferedReader(USERS_FILE, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] p = line.split(",", -1);
                if (p.length < 5) continue;
                USERS.put(p[0], new UserRecord(p[0], p[1], p[2], p[3], p[4]));
            }
        } catch (IOException e) {
            // ignore, start empty
        }
    }

    private static void persistUsers() {
        StringBuilder sb = new StringBuilder();
        for (UserRecord r : USERS.values()) {
            sb.append(r.username).append(",")
                    .append(r.email).append(",")
                    .append(r.salt).append(",")
                    .append(r.hash).append(",")
                    .append(r.status).append("\n");
        }
        try {
            Files.writeString(USERS_FILE, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            // best-effort; caller already updated cache
        }
    }

    private static String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(hexToBytes(salt));
            byte[] digest = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean slowEquals(String a, String b) {
        // constant-time-ish comparison
        if (a == null || b == null) return false;
        int diff = a.length() ^ b.length();
        for (int i = 0; i < Math.min(a.length(), b.length()); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    private static String randomSalt() {
        byte[] buf = new byte[16];
        new SecureRandom().nextBytes(buf);
        return bytesToHex(buf);
    }

    private static String genTempPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }

    private static String bytesToHex(byte[] arr) {
        StringBuilder sb = new StringBuilder(arr.length * 2);
        for (byte b : arr) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte)((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }

    // simple record
    private static class UserRecord {
        String username, email, salt, hash, status;
        UserRecord(String u, String e, String s, String h, String st) {
            this.username = u; this.email = e; this.salt = s; this.hash = h; this.status = st;
        }
    }
}
