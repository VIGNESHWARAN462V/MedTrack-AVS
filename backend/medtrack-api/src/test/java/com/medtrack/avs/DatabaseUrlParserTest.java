package com.medtrack.avs;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseUrlParserTest {

    @AfterEach
    void tearDown() {
        System.clearProperty("DB_URL");
        System.clearProperty("DB_USERNAME");
        System.clearProperty("DB_PASSWORD");
        System.clearProperty("spring.datasource.url");
        System.clearProperty("spring.datasource.username");
        System.clearProperty("spring.datasource.password");
        System.clearProperty("MYSQL_PRIVATE_URL");
        System.clearProperty("MYSQL_URL");
    }

    @Test
    void testParseRailwayMysqlPrivateUrl() {
        String testUrl = "mysql://root:superSecret123@mysql.railway.internal:3306/railway";
        MedtrackApiApplication.parseAndApplyDatabaseUrl(testUrl);

        assertEquals("jdbc:mariadb://mysql.railway.internal:3306/railway?useSSL=false&allowPublicKeyRetrieval=true&timezone=UTC",
                System.getProperty("DB_URL"));
        assertEquals("root", System.getProperty("DB_USERNAME"));
        assertEquals("superSecret123", System.getProperty("DB_PASSWORD"));
        assertEquals(System.getProperty("DB_URL"), System.getProperty("spring.datasource.url"));
        assertEquals("root", System.getProperty("spring.datasource.username"));
        assertEquals("superSecret123", System.getProperty("spring.datasource.password"));
    }

    @Test
    void testParseUrlWithEncodedCharactersAndQuery() {
        String testUrl = "mysql://medtrack%20user:p%40ssword!@db.internal:3307/custom_db?ssl=true";
        MedtrackApiApplication.parseAndApplyDatabaseUrl(testUrl);

        assertEquals("jdbc:mariadb://db.internal:3307/custom_db?useSSL=false&allowPublicKeyRetrieval=true&timezone=UTC",
                System.getProperty("DB_URL"));
        assertEquals("medtrack user", System.getProperty("DB_USERNAME"));
        assertEquals("p@ssword!", System.getProperty("DB_PASSWORD"));
    }

    @Test
    void testConfigureDatabasePropertiesViaSystemProperty() {
        System.setProperty("MYSQL_PRIVATE_URL", "mysql://admin:pw@host.internal:3306/production_db");
        MedtrackApiApplication.configureDatabaseProperties();

        assertEquals("jdbc:mariadb://host.internal:3306/production_db?useSSL=false&allowPublicKeyRetrieval=true&timezone=UTC",
                System.getProperty("DB_URL"));
        assertEquals("admin", System.getProperty("DB_USERNAME"));
        assertEquals("pw", System.getProperty("DB_PASSWORD"));
    }
}
