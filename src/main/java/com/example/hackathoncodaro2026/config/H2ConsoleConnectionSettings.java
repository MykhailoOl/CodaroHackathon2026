package com.example.hackathoncodaro2026.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

@Component
public class H2ConsoleConnectionSettings implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(H2ConsoleConnectionSettings.class);
    private static final String H2_SERVLET = "org.h2.server.web.JakartaWebServlet";

    private final Environment environment;

    public H2ConsoleConnectionSettings(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!(bean instanceof ServletRegistrationBean<?> registration)) {
            return bean;
        }
        Object servlet;
        try {
            servlet = registration.getServlet();
        } catch (RuntimeException ex) {
            return bean;
        }
        if (servlet == null || !H2_SERVLET.equals(servlet.getClass().getName())) {
            return bean;
        }
        Path directory = Path.of("data").toAbsolutePath().normalize();
        Path file = writeSettings(directory);
        if (file != null) {
            registration.addInitParameter("properties", directory.toString().replace('\\', '/'));
        }
        return bean;
    }

    private Path writeSettings(Path directory) {
        try {
            Files.createDirectories(directory);
            Path file = directory.resolve(".h2.server.properties");
            String url = environment.getProperty("spring.datasource.url", "jdbc:h2:file:./data/everrest;LOCK_TIMEOUT=5000");
            String user = environment.getProperty("spring.datasource.username", "sa");
            Properties properties = new Properties();
            properties.setProperty("0", "EverRest (Embedded)|org.h2.Driver|" + url + "|" + user);
            properties.setProperty("webAllowOthers", "false");
            properties.setProperty("webPort", "8082");
            properties.setProperty("webSSL", "false");
            try (OutputStream out = Files.newOutputStream(file)) {
                properties.store(out, "H2 Server Properties");
            }
            return file;
        } catch (Exception ex) {
            log.warn("Could not write H2 console connection settings: {}", ex.getMessage());
            return null;
        }
    }
}
