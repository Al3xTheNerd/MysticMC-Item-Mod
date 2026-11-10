package co.mysticmc.items.capture;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Sends a saved capture using the four multipart fields expected by the Item Site. */
final class ItemCaptureUpload {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private ItemCaptureUpload() {}

    static CompletableFuture<Integer> send(String endpoint, String apiKey, String json,
                                           ItemCaptureFiles.SavedCapture capture) throws IOException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API key is empty");
        }
        URI uri = endpointUri(endpoint);
        String boundary = "mystic-item-browser-" + UUID.randomUUID();
        List<HttpRequest.BodyPublisher> parts = new ArrayList<>();
        parts.add(textPart(boundary, "apiKey", "text/plain", apiKey));
        parts.add(textPart(boundary, "json", "application/json", json));
        parts.add(textPart(boundary, "extension", "text/plain", capture.extension()));
        parts.add(bytes("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"image\"; filename=\"preview"
                + capture.extension() + "\"\r\n"
                + "Content-Type: image/" + capture.extension().substring(1) + "\r\n\r\n"));
        parts.add(HttpRequest.BodyPublishers.ofFile(capture.imagePath()));
        parts.add(bytes("\r\n--" + boundary + "--\r\n"));

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.concat(parts.toArray(HttpRequest.BodyPublisher[]::new)))
                .build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenApply(HttpResponse::statusCode);
    }

    private static URI endpointUri(String endpoint) {
        URI uri;
        try {
            uri = URI.create(endpoint.trim());
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new IllegalArgumentException("API endpoint is not a valid URL", error);
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                || "[::1]".equals(host) || "::1".equals(host);
        if (host == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                || !("https".equalsIgnoreCase(scheme) || (loopback && "http".equalsIgnoreCase(scheme)))) {
            throw new IllegalArgumentException("API endpoint must use HTTPS (or HTTP on localhost)");
        }
        return uri;
    }

    private static HttpRequest.BodyPublisher textPart(String boundary, String name,
                                                      String contentType, String value) {
        return bytes("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n"
                + "Content-Type: " + contentType + "; charset=UTF-8\r\n\r\n"
                + value + "\r\n");
    }

    private static HttpRequest.BodyPublisher bytes(String value) {
        return HttpRequest.BodyPublishers.ofByteArray(value.getBytes(StandardCharsets.UTF_8));
    }
}
