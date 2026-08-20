package com.example.hackathoncodaro2026.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(prefix = "app.browser", name = "auto-open", havingValue = "true")
public class BrowserLauncher implements ApplicationListener<ApplicationReadyEvent> {

    private static final String OPENED_FLAG = "app.browser.auto-open.done";
    private static final Logger log = LoggerFactory.getLogger(BrowserLauncher.class);

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if ("true".equals(System.getProperty(OPENED_FLAG))) {
            return;
        }
        Environment env = event.getApplicationContext().getEnvironment();
        int port = resolvePort(event, env);
        String contextPath = normalizeContextPath(env.getProperty("server.servlet.context-path", ""));
        String loginUrl = "http://localhost:" + port + contextPath + "/login";
        boolean h2Enabled = Boolean.TRUE.equals(env.getProperty("spring.h2.console.enabled", Boolean.class, false));
        String h2Url = null;
        if (h2Enabled && Boolean.TRUE.equals(env.getProperty("app.browser.open-h2-console", Boolean.class, false))) {
            h2Url = "http://localhost:" + port + contextPath + "/h2-launch";
        }
        String executable = env.getProperty("app.browser.executable", "firefox");
        System.setProperty(OPENED_FLAG, "true");
        try {
            if (!openWithFirefox(executable, loginUrl, h2Url)) {
                openWithDesktop(loginUrl, h2Url);
            }
        } catch (Exception ex) {
            log.warn("Could not open the browser: {}", ex.getMessage());
        }
    }

    private int resolvePort(ApplicationReadyEvent event, Environment env) {
        if (event.getApplicationContext() instanceof WebServerApplicationContext webContext
                && webContext.getWebServer() != null) {
            int port = webContext.getWebServer().getPort();
            if (port > 0) {
                return port;
            }
        }
        Integer localPort = env.getProperty("local.server.port", Integer.class);
        if (localPort != null && localPort > 0) {
            return localPort;
        }
        return env.getProperty("server.port", Integer.class, 8080);
    }

    private String normalizeContextPath(String contextPath) {
        if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath)) {
            return "";
        }
        if (contextPath.endsWith("/")) {
            return contextPath.substring(0, contextPath.length() - 1);
        }
        return contextPath;
    }

    private boolean openWithFirefox(String configured, String loginUrl, String h2Url) {
        String firefox = resolveFirefox(configured);
        if (firefox == null) {
            log.warn("Firefox was not found; falling back to the default browser");
            return false;
        }
        List<String> command = new ArrayList<>();
        command.add(firefox);
        command.add(loginUrl);
        if (h2Url != null) {
            command.add(h2Url);
        }
        try {
            new ProcessBuilder(command).start();
            return true;
        } catch (Exception ex) {
            log.warn("Could not start Firefox: {}", ex.getMessage());
            return false;
        }
    }

    private String resolveFirefox(String configured) {
        List<String> candidates = new ArrayList<>();
        if (configured != null && !configured.isBlank()) {
            candidates.add(configured);
            if (!configured.endsWith(".exe") && !configured.contains("\\") && !configured.contains("/")) {
                candidates.add(configured + ".exe");
            }
        }
        candidates.add("C:\\Program Files\\Mozilla Firefox\\firefox.exe");
        candidates.add("C:\\Program Files (x86)\\Mozilla Firefox\\firefox.exe");
        for (String candidate : candidates) {
            Path path = Path.of(candidate);
            if (path.isAbsolute() && Files.isRegularFile(path)) {
                return path.toString();
            }
        }
        String fromPath = findOnPath("firefox.exe");
        if (fromPath != null) {
            return fromPath;
        }
        return findOnPath("firefox");
    }

    private String findOnPath(String name) {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (String dir : path.split(Pattern.quote(File.pathSeparator))) {
            if (dir.isBlank()) {
                continue;
            }
            Path candidate = Path.of(dir.trim(), name);
            if (Files.isRegularFile(candidate)) {
                return candidate.toString();
            }
        }
        return null;
    }

    private void openWithDesktop(String loginUrl, String h2Url) {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            log.warn("No browser could be opened automatically");
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create(loginUrl));
            if (h2Url != null) {
                Desktop.getDesktop().browse(URI.create(h2Url));
            }
        } catch (Exception ex) {
            log.warn("Could not open the default browser: {}", ex.getMessage());
        }
    }
}
