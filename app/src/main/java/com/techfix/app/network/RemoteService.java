package com.techfix.app.network;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Small platform-only HTTP client; the production API URL can be supplied by the server configuration. */
public final class RemoteService {
    private RemoteService() { }

    public static String get(String endpoint) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8);
        } finally { connection.disconnect(); }
    }

    public static String post(String endpoint, String jsonBody) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setDoOutput(true);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }
        try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8);
        } finally { connection.disconnect(); }
    }
}

