package com.trustlayer.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Executes only when explicitly enabled against a real local MySQL 8 instance.
 * It is deliberately opt-in so ordinary unit tests do not require a developer's database.
 */
@SpringBootTest
@ActiveProfiles("integration")
@EnabledIfEnvironmentVariable(
        named = "TRUSTLAYER_RUN_MYSQL_INTEGRATION_TESTS",
        matches = "true"
)
class MysqlSchemaMigrationIntegrationTest {

    private static final Set<String> REQUIRED_TABLES = Set.of(
            "users",
            "user_preferences",
            "authentication_sessions",
            "threats",
            "scans",
            "scan_results",
            "transactions",
            "risk_indicators",
            "reports",
            "audit_logs",
            "email_verification_tokens",
            "password_reset_tokens",
            "extension_authorization_codes"
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayAppliesTheCompleteCurrentSchemaToMysql() {
        List<String> tableNames = jdbcTemplate.queryForList(
                """
                        SELECT table_name
                        FROM information_schema.tables
                        WHERE table_schema = DATABASE()
                        """,
                String.class
        );

        assertThat(tableNames).containsAll(REQUIRED_TABLES);

        Integer successfulMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE",
                Integer.class
        );

        assertThat(successfulMigrations).isEqualTo(4);
    }
}
