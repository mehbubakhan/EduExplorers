//package com.server.server;
//
//import java.io.*;
//import java.net.*;
//import java.util.*;
//import java.util.concurrent.*;
//
//public class CollabServer {
//    private static final int PORT = 6001;
//
//    // room -> set of clients
//    private static final ConcurrentHashMap<String, CopyOnWriteArraySet<Client>> rooms = new ConcurrentHashMap<>();
//
//    public static void main(String[] args) throws IOException {
//        try (ServerSocket ss = new ServerSocket(PORT)) {
//            System.out.println("CollabServer listening on " + PORT);
//            while (true) {
//                Socket s = ss.accept();
//                new Thread(() -> handle(s)).start();
//            }
//        }
//    }
//
//    private static void handle(Socket s) {
//        Client client = null;
//        try (BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
//             PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8"), true)) {
//
//            // Expect: HELLO <username> <room>
//            String hello = in.readLine();
//            if (hello == null || !hello.startsWith("HELLO ")) {
//                out.println("ERR Expected HELLO <username> <room>");
//                return;
//            }
//            String[] parts = hello.split("\\s+", 3);
//            if (parts.length < 3) {
//                out.println("ERR Bad HELLO");
//                return;
//            }
//            String username = parts[1].trim();
//            String room = parts[2].trim();
//            if (username.isEmpty() || room.isEmpty()) {
//                out.println("ERR Missing username/room");
//                return;
//            }
//
//            client = new Client(username, room, s, out);
//            rooms.computeIfAbsent(room, k -> new CopyOnWriteArraySet<>()).add(client);
//            out.println("OK");
//
//            // Notify others someone joined (optional)
//            broadcast(room, "INFO " + username + " joined", null);
//
//            String line;
//            while ((line = in.readLine()) != null) {
//                if (line.startsWith("DRAW ")) {
//                    // DRAW <phase> <x> <y> <colorHex> <width>
//                    broadcast(room, "EVT " + username + " " + line.substring(5), client);
//                } else if (line.equals("PING")) {
//                    out.println("PONG");
//                } else {
//                    out.println("ERR Unknown cmd");
//                }
//            }
//        } catch (IOException ignored) {
//        } finally {
//            if (client != null) {
//                CopyOnWriteArraySet<Client> set = rooms.get(client.room);
//                if (set != null) {
//                    set.remove(client);
//                    if (set.isEmpty()) rooms.remove(client.room);
//                    broadcast(client.room, "INFO " + client.username + " left", null);
//                }
//            }
//            try { s.close(); } catch (Exception ignored) {}
//        }
//    }
//
//    private static void broadcast(String room, String msg, Client except) {
//        CopyOnWriteArraySet<Client> set = rooms.get(room);
//        if (set == null) return;
//        for (Client c : set) {
//            if (c == except) continue; // echo is optional; comment this to echo to sender too
//            c.out.println(msg);
//        }
//    }
//
//    private static class Client {
//        final String username, room;
//        final Socket socket;
//        final PrintWriter out;
//        Client(String u, String r, Socket s, PrintWriter o) {
//            this.username = u; this.room = r; this.socket = s; this.out = o;
//        }
//    }
//}

package com.server.server;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class CollabServer {
    private static final int PORT = 6001;
    private static final ConcurrentHashMap<String, CopyOnWriteArraySet<Client>> rooms = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        try (ServerSocket ss = new ServerSocket(PORT)) {
            System.out.println("CollabServer listening on " + PORT);
            while (true) {
                Socket s = ss.accept();
                new Thread(() -> handle(s)).start();
            }
        }
    }

    private static void handle(Socket s) {
        Client client = null;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8"), true)) {

            String hello = in.readLine();
            if (hello == null || !hello.startsWith("HELLO ")) {
                out.println("ERR Expected HELLO <username> <room>");
                return;
            }
            String[] parts = hello.split("\\s+", 3);
            if (parts.length < 3) {
                out.println("ERR Bad HELLO");
                return;
            }
            String username = parts[1].trim();
            String room = parts[2].trim();
            if (username.isEmpty() || room.isEmpty()) {
                out.println("ERR Missing username/room");
                return;
            }

            client = new Client(username, room, s, out);
            rooms.computeIfAbsent(room, k -> new CopyOnWriteArraySet<>()).add(client);
            out.println("OK");

            broadcast(room, "INFO " + username + " joined", null);

            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("DRAW ")) {
                    broadcast(room, "EVT " + username + " " + line.substring(5), client);
                } else if (line.startsWith("CHAT ")) {
                    String msg = line.substring(5);
                    broadcast(room, "CHAT " + username + " " + msg, null);
                } else if (line.equals("LEAVE")) {
                    break;
                } else if (line.equals("PING")) {
                    out.println("PONG");
                } else {
                    out.println("ERR Unknown cmd");
                }
            }
        } catch (IOException ignored) {
        } finally {
            if (client != null) {
                CopyOnWriteArraySet<Client> set = rooms.get(client.room);
                if (set != null) {
                    set.remove(client);
                    if (set.isEmpty()) rooms.remove(client.room);
                    broadcast(client.room, "INFO " + client.username + " left", null);
                }
            }
            try { s.close(); } catch (Exception ignored) {}
        }
    }

    private static void broadcast(String room, String msg, Client except) {
        CopyOnWriteArraySet<Client> set = rooms.get(room);
        if (set == null) return;
        for (Client c : set) {
            if (c == except) continue;
            c.out.println(msg);
        }
    }

    private static class Client {
        final String username, room;
        final Socket socket;
        final PrintWriter out;
        Client(String u, String r, Socket s, PrintWriter o) {
            this.username = u; this.room = r; this.socket = s; this.out = o;
        }
    }
}
