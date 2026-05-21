package com.todo.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 请求工具类 —— 封装 POST / GET 通用逻辑。
 *
 * 所有方法默认短超时（5s），适用内部服务间调用。
 * 流式响应（SSE）请自行处理连接和读取。
 */
@Slf4j
public class HttpClientUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HttpClientUtils() {}

    // ==================== POST ====================

    /**
     * POST 请求，JSON body，返回解析后的 Map。
     *
     * @param url      目标 URL
     * @param headers  请求头（可传 null 或空 Map）
     * @param body     请求体（自动转 JSON）
     * @return         Map 格式响应体，请求失败返回 {"error": "..."}
     */
    public static Map<String, Object> postJson(String url, Map<String, String> headers, Object body) {
        return execute("POST", url, headers, body, 5000);
    }

    /**
     * POST 请求（无自定义头），JSON body，返回解析后的 Map。
     */
    public static Map<String, Object> postJson(String url, Object body) {
        return postJson(url, null, body);
    }

    // ==================== GET ====================

    /**
     * GET 请求，返回解析后的 Map。
     *
     * @param url      目标 URL
     * @param headers  请求头（可传 null 或空 Map）
     * @return         Map 格式响应体，请求失败返回 {"error": "..."}
     */
    public static Map<String, Object> get(String url, Map<String, String> headers) {
        return execute("GET", url, headers, null, 5000);
    }

    /**
     * GET 请求（无自定义头），返回解析后的 Map。
     */
    public static Map<String, Object> get(String url) {
        return get(url, null);
    }

    // ==================== 底层执行 ====================

    /**
     * 打开 HTTP 连接并写入请求体，连接准备好后可读取响应（适用 SSE 等流式场景）。
     *
     * 调用方负责读取后调用 conn.disconnect() 关闭连接。
     *
     * @param method   HTTP 方法
     * @param url      目标 URL
     * @param headers  请求头
     * @param body     请求体（POST/PUT，GET 传 null）
     * @param connectTimeout  连接超时（毫秒）
     * @param readTimeout     读取超时（毫秒）
     * @return         已写入请求体的 HttpURLConnection
     */
    public static HttpURLConnection openConnection(String method, String url,
                                                    Map<String, String> headers,
                                                    Object body,
                                                    int connectTimeout, int readTimeout) throws IOException {
        URL targetUrl = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) targetUrl.openConnection();
        conn.setRequestMethod(method.toUpperCase());
        conn.setConnectTimeout(connectTimeout);
        conn.setReadTimeout(readTimeout);

        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                conn.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }

        if (body != null && !method.equalsIgnoreCase("GET")) {
            conn.setDoOutput(true);
            if (conn.getRequestProperty("Content-Type") == null) {
                conn.setRequestProperty("Content-Type", "application/json");
            }
            try (OutputStream os = conn.getOutputStream()) {
                os.write(OBJECT_MAPPER.writeValueAsBytes(body));
                os.flush();
            }
        }

        return conn;
    }

    /**
     * 通用 HTTP 请求执行。
     *
     * @param method   HTTP 方法（GET / POST / PUT / DELETE）
     * @param url      目标 URL
     * @param headers  请求头
     * @param body     请求体（仅 POST/PUT 需要，GET/DELETE 传 null）
     * @param timeout  超时时间（毫秒）
     * @return         Map 格式响应体
     */
    public static Map<String, Object> execute(String method, String url,
                                               Map<String, String> headers,
                                               Object body, int timeout) {
        HttpURLConnection conn = null;
        try {
            URL targetUrl = new URL(url);
            conn = (HttpURLConnection) targetUrl.openConnection();
            conn.setRequestMethod(method.toUpperCase());
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);

            // 设置请求头
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            // 写入请求体（POST / PUT）
            if (body != null && !method.equalsIgnoreCase("GET")) {
                conn.setDoOutput(true);
                if (conn.getRequestProperty("Content-Type") == null) {
                    conn.setRequestProperty("Content-Type", "application/json");
                }
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(OBJECT_MAPPER.writeValueAsBytes(body));
                    os.flush();
                }
            }

            int status = conn.getResponseCode();
            if (status >= 200 && status < 300) {
                try (InputStream is = conn.getInputStream()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> result = OBJECT_MAPPER.readValue(is, Map.class);
                    return result;
                }
            } else {
                String errorBody = readStream(conn.getErrorStream());
                log.warn("HTTP {} {} failed: status={}, body={}", method, url, status, errorBody);
            }
        } catch (Exception e) {
            log.error("HTTP {} {} error", method, url, e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return Collections.singletonMap("error", "请求失败");
    }

    /**
     * 读取 InputStream 为字符串。
     */
    public static String readStream(InputStream stream) {
        if (stream == null) return "";
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[4096];
            int n;
            while ((n = stream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, n);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
