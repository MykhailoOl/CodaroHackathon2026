package com.example.hackathoncodaro2026.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-ID";
    public static final String MDC_KEY = "requestId";

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._-]{8,128}$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long started = System.nanoTime();
        String requestId = resolveRequestId(request.getHeader(HEADER));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (shouldLog(request)) {
                int status = response.getStatus();
                long durationMs = (System.nanoTime() - started) / 1_000_000L;
                log.info(
                        "method={} path={} status={} durationMs={}",
                        request.getMethod(),
                        pathWithoutQuery(request),
                        status,
                        durationMs
                );
            }
            MDC.clear();
        }
    }

    static String resolveRequestId(String header) {
        if (header != null && SAFE_REQUEST_ID.matcher(header.trim()).matches()) {
            return header.trim();
        }
        return UUID.randomUUID().toString();
    }

    static boolean shouldLog(HttpServletRequest request) {
        String path = pathWithoutQuery(request).toLowerCase(Locale.ROOT);
        if (path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/")
                || path.startsWith("/h2-console")
                || path.equals("/favicon.ico")) {
            return false;
        }
        return !(path.endsWith(".css")
                || path.endsWith(".js")
                || path.endsWith(".map")
                || path.endsWith(".png")
                || path.endsWith(".jpg")
                || path.endsWith(".jpeg")
                || path.endsWith(".webp")
                || path.endsWith(".gif")
                || path.endsWith(".ico")
                || path.endsWith(".svg")
                || path.endsWith(".woff")
                || path.endsWith(".woff2"));
    }

    static String pathWithoutQuery(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isBlank()) {
            return "/";
        }
        int query = uri.indexOf('?');
        if (query >= 0) {
            return uri.substring(0, query);
        }
        return uri;
    }
}
