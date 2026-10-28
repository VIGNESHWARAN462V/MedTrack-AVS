package com.medtrack.avs;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@SpringBootApplication
@EnableCaching
public class MedtrackApiApplication {

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        loadDotenv();
        configureDatabaseProperties();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(MedtrackApiApplication.class, args);
    }

    private static void loadDotenv() {
        java.nio.file.Path[] candidates = new java.nio.file.Path[] {
            java.nio.file.Paths.get(".env"),
            java.nio.file.Paths.get("backend/medtrack-api/.env"),
            java.nio.file.Paths.get("../.env")
        };
        for (java.nio.file.Path path : candidates) {
            if (java.nio.file.Files.exists(path) && java.nio.file.Files.isRegularFile(path)) {
                try (java.io.BufferedReader reader = java.nio.file.Files.newBufferedReader(path, java.nio.charset.StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        int eqIndex = line.indexOf('=');
                        if (eqIndex > 0) {
                            String key = line.substring(0, eqIndex).trim();
                            String value = line.substring(eqIndex + 1).trim();
                            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                                (value.startsWith("'") && value.endsWith("'"))) {
                                value = value.substring(1, value.length() - 1);
                            }
                            if (System.getProperty(key) == null && System.getenv(key) == null) {
                                System.setProperty(key, value);
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
                break;
            }
        }
    }

    static void configureDatabaseProperties() {
        String dbUrlCandidate = getEnvOrProperty("MYSQL_PRIVATE_URL");
        if (dbUrlCandidate == null || dbUrlCandidate.isBlank()) {
            dbUrlCandidate = getEnvOrProperty("MYSQL_URL");
        }
        if (dbUrlCandidate != null && !dbUrlCandidate.isBlank()) {
            parseAndApplyDatabaseUrl(dbUrlCandidate.trim());
        }
    }

    private static String getEnvOrProperty(String key) {
        String val = System.getProperty(key);
        if (val == null || val.isBlank()) {
            val = System.getenv(key);
        }
        return val;
    }

    static void parseAndApplyDatabaseUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return;
        }
        try {
            if (rawUrl.startsWith("jdbc:mysql://") || rawUrl.startsWith("jdbc:mariadb://")) {
                System.setProperty("DB_URL", rawUrl);
                System.setProperty("spring.datasource.url", rawUrl);
                return;
            }

            String scheme = "";
            String remaining = rawUrl;
            if (remaining.startsWith("mysql://")) {
                scheme = "mysql://";
                remaining = remaining.substring("mysql://".length());
            } else if (remaining.startsWith("mariadb://")) {
                scheme = "mariadb://";
                remaining = remaining.substring("mariadb://".length());
            }

            if (!scheme.isEmpty()) {
                String username = null;
                String password = null;

                int atIndex = remaining.lastIndexOf('@');
                if (atIndex > 0) {
                    String userPart = remaining.substring(0, atIndex);
                    remaining = remaining.substring(atIndex + 1);

                    int colonIndex = userPart.indexOf(':');
                    if (colonIndex >= 0) {
                        username = java.net.URLDecoder.decode(userPart.substring(0, colonIndex), java.nio.charset.StandardCharsets.UTF_8);
                        password = java.net.URLDecoder.decode(userPart.substring(colonIndex + 1), java.nio.charset.StandardCharsets.UTF_8);
                    } else {
                        username = java.net.URLDecoder.decode(userPart, java.nio.charset.StandardCharsets.UTF_8);
                    }
                }

                String hostPort;
                String database = "railway";
                int slashIndex = remaining.indexOf('/');
                if (slashIndex >= 0) {
                    hostPort = remaining.substring(0, slashIndex);
                    String pathPart = remaining.substring(slashIndex + 1);
                    int questionIndex = pathPart.indexOf('?');
                    if (questionIndex >= 0) {
                        database = pathPart.substring(0, questionIndex);
                    } else {
                        database = pathPart;
                    }
                } else {
                    hostPort = remaining;
                }

                if (database.isBlank()) {
                    database = "railway";
                }

                String host = hostPort;
                int port = 3306;
                int colonHostIndex = hostPort.indexOf(':');
                if (colonHostIndex >= 0) {
                    host = hostPort.substring(0, colonHostIndex);
                    try {
                        port = Integer.parseInt(hostPort.substring(colonHostIndex + 1));
                    } catch (NumberFormatException ignored) {
                        port = 3306;
                    }
                }

                String jdbcUrl = "jdbc:mariadb://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true&timezone=UTC";

                System.setProperty("DB_URL", jdbcUrl);
                System.setProperty("spring.datasource.url", jdbcUrl);
                if (username != null && !username.isBlank()) {
                    System.setProperty("DB_USERNAME", username);
                    System.setProperty("spring.datasource.username", username);
                }
                if (password != null) {
                    System.setProperty("DB_PASSWORD", password);
                    System.setProperty("spring.datasource.password", password);
                }
            }
        } catch (Exception e) {
            System.err.println("[MedTrack] Warning: Failed to parse MYSQL_PRIVATE_URL/MYSQL_URL: " + e.getMessage());
        }
    }
}
