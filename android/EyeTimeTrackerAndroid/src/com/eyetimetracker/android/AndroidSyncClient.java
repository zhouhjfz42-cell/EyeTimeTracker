package com.eyetimetracker.android;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class AndroidSyncClient {
    // 单行响应的字符上限，防止对端异常或数据膨胀时一次性读入超大字符串导致 OOM
    private static final int MAX_RESPONSE_CHARS = 8 * 1024 * 1024;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    public AndroidSyncClient() {
        // PC 端每次同步要载入/合并/保存整个状态文件，响应可能超过 10 秒，读超时要留足
        this(3000, 15000);
    }

    public AndroidSyncClient(int connectTimeoutMillis, int readTimeoutMillis) {
        this.connectTimeoutMillis = Math.max(1, connectTimeoutMillis);
        this.readTimeoutMillis = Math.max(1, readTimeoutMillis);
    }

    public String sendJson(SyncSettings settings, String requestJson) {
        if (settings == null) {
            return "";
        }
        if (!settings.isPaired || isBlank(settings.peerHost) || settings.peerPort <= 0) {
            settings.lastError = "Sync settings are incomplete.";
            return "";
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(settings.peerHost, settings.peerPort), connectTimeoutMillis);
            socket.setSoTimeout(readTimeoutMillis);
            try (
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                writer.write(requestJson == null ? "" : requestJson);
                writer.newLine();
                writer.flush();

                String response = readLineBounded(reader);
                settings.lastError = "";
                return response == null ? "" : response;
            }
        } catch (OutOfMemoryError oom) {
            settings.lastError = "Sync response is too large.";
            return "";
        } catch (Exception ex) {
            settings.lastError = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return "";
        }
    }

    public String sendJson(String host, int port, String requestJson, SyncSettings settings) {
        if (settings == null) {
            settings = new SyncSettings();
        }
        if (isBlank(host) || port <= 0) {
            settings.lastError = "PC address is incomplete.";
            return "";
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
            socket.setSoTimeout(readTimeoutMillis);
            try (
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                writer.write(requestJson == null ? "" : requestJson);
                writer.newLine();
                writer.flush();

                String response = readLineBounded(reader);
                settings.lastError = "";
                return response == null ? "" : response;
            }
        } catch (OutOfMemoryError oom) {
            settings.lastError = "Sync response is too large.";
            return "";
        } catch (Exception ex) {
            settings.lastError = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return "";
        }
    }

    private static String readLineBounded(BufferedReader reader) throws java.io.IOException {
        StringBuilder line = new StringBuilder(8192);
        int ch;
        boolean sawAny = false;
        while ((ch = reader.read()) != -1) {
            sawAny = true;
            if (ch == '\n') {
                break;
            }
            if (line.length() >= MAX_RESPONSE_CHARS) {
                throw new java.io.IOException("Sync response exceeded " + MAX_RESPONSE_CHARS + " chars.");
            }
            if (ch != '\r') {
                line.append((char) ch);
            }
        }
        if (!sawAny) {
            return null;
        }
        return line.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
