package com.example.hackathoncodaro2026.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaEnumConstraintRepair implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaEnumConstraintRepair.class);

    private final JdbcTemplate jdbcTemplate;

    public SchemaEnumConstraintRepair(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> roles = List.of("ADMIN", "USER", "MANAGER");
        dropMatchingChecks("APP_USERS", "ROLE", roles);
        dropMatchingChecks("RESERVATIONS", "STATUS", List.of("CONFIRMED", "CANCELLED", "PENDING"));
        dropMatchingChecks("RESERVATIONS", "PAYMENT_METHOD", List.of("CASH", "CARD_ON_SITE", "ONLINE_TRANSFER"));
        dropMatchingChecks("RESERVATIONS", "SERVICE_TYPE", List.of(
                "BURIAL_CEREMONY", "CREMATION_CEREMONY", "MEMORIAL_SERVICE", "FAREWELL_CEREMONY"
        ));
        dropMatchingChecks("RESERVATIONS", "PACKAGE_CODE", List.of("ESSENTIAL", "CLASSIC", "TRIBUTE"));
        dropMatchingChecks("SERVICE_VENUES", "TYPE", List.of(
                "CHAPEL", "CEREMONY_HALL", "CREMATORIUM", "MEMORIAL_GARDEN", "RECEPTION_HALL"
        ));
        dropMatchingChecks("ARRANGEMENT_EXTRAS", "PRICING_MODE", List.of("FIXED", "PER_ATTENDEE"));
        dropMatchingChecks("NOTIFICATIONS", "TYPE", List.of(
                "RESERVATION_CREATED", "RESERVATION_UPDATED", "RESERVATION_CONFIRMED", "RESERVATION_CANCELLED"
        ));
        widenColumn("APP_USERS", "ROLE");
        widenColumn("RESERVATIONS", "STATUS");
        widenColumn("RESERVATIONS", "PAYMENT_METHOD");
        widenColumn("RESERVATIONS", "SERVICE_TYPE");
        widenColumn("RESERVATIONS", "PACKAGE_CODE");
        widenColumn("SERVICE_VENUES", "TYPE");
        widenColumn("ARRANGEMENT_EXTRAS", "PRICING_MODE");
        widenColumn("NOTIFICATIONS", "TYPE");
        makeColumnNullable("RESERVATION_EXTRAS", "ITEM_ID");
    }

    private void dropMatchingChecks(String table, String column, List<String> enumTokens) {
        for (ConstraintRef constraint : findChecks(table, column, enumTokens)) {
            try {
                jdbcTemplate.execute(
                        "ALTER TABLE " + quote(constraint.tableName()) + " DROP CONSTRAINT " + quote(constraint.name())
                );
                log.info("Dropped leftover enum check {} on {}.{}", constraint.name(), constraint.tableName(), column);
            } catch (Exception ex) {
                log.warn("Could not drop check {} on {}: {}", constraint.name(), constraint.tableName(), ex.getMessage());
            }
        }
    }

    private List<ConstraintRef> findChecks(String table, String column, List<String> enumTokens) {
        Set<ConstraintRef> found = new LinkedHashSet<>();
        found.addAll(findChecksFromColumnUsage(table, column));
        found.addAll(findChecksFromClause(table, column, enumTokens));
        return new ArrayList<>(found);
    }

    private List<ConstraintRef> findChecksFromColumnUsage(String table, String column) {
        String sql = """
                SELECT tc.CONSTRAINT_NAME, tc.TABLE_NAME
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                JOIN INFORMATION_SCHEMA.CONSTRAINT_COLUMN_USAGE ccu
                  ON tc.CONSTRAINT_CATALOG = ccu.CONSTRAINT_CATALOG
                 AND tc.CONSTRAINT_SCHEMA = ccu.CONSTRAINT_SCHEMA
                 AND tc.CONSTRAINT_NAME = ccu.CONSTRAINT_NAME
                WHERE tc.CONSTRAINT_TYPE = 'CHECK'
                  AND UPPER(tc.TABLE_NAME) = ?
                  AND UPPER(ccu.COLUMN_NAME) = ?
                """;
        try {
            return jdbcTemplate.query(sql, (rs, rowNum) -> new ConstraintRef(
                    rs.getString("CONSTRAINT_NAME"),
                    rs.getString("TABLE_NAME")
            ), table, column);
        } catch (Exception ex) {
            log.debug("Column-usage check lookup skipped: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<ConstraintRef> findChecksFromClause(String table, String column, List<String> enumTokens) {
        String sql = """
                SELECT tc.CONSTRAINT_NAME, tc.TABLE_NAME, cc.CHECK_CLAUSE
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc
                  ON tc.CONSTRAINT_CATALOG = cc.CONSTRAINT_CATALOG
                 AND tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA
                 AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
                WHERE tc.CONSTRAINT_TYPE = 'CHECK'
                  AND UPPER(tc.TABLE_NAME) = ?
                """;
        List<ConstraintRef> matches = new ArrayList<>();
        try {
            jdbcTemplate.query(sql, rs -> {
                String clause = rs.getString("CHECK_CLAUSE");
                if (clauseMatches(clause, column, enumTokens)) {
                    matches.add(new ConstraintRef(rs.getString("CONSTRAINT_NAME"), rs.getString("TABLE_NAME")));
                }
            }, table);
        } catch (Exception ex) {
            log.debug("Check-clause lookup skipped: {}", ex.getMessage());
        }
        return matches;
    }

    private boolean clauseMatches(String clause, String column, List<String> enumTokens) {
        if (clause == null || clause.isBlank()) {
            return false;
        }
        String upper = clause.toUpperCase(Locale.ROOT);
        if (upper.contains(column.toUpperCase(Locale.ROOT))) {
            return true;
        }
        int hits = 0;
        for (String token : enumTokens) {
            if (upper.contains("'" + token.toUpperCase(Locale.ROOT) + "'")) {
                hits++;
            }
        }
        return hits >= 2;
    }

    private void widenColumn(String table, String column) {
        try {
            jdbcTemplate.query(
                    """
                            SELECT TABLE_NAME, COLUMN_NAME
                            FROM INFORMATION_SCHEMA.COLUMNS
                            WHERE UPPER(TABLE_NAME) = ? AND UPPER(COLUMN_NAME) = ?
                            """,
                    rs -> {
                        String tableName = rs.getString("TABLE_NAME");
                        String columnName = rs.getString("COLUMN_NAME");
                        try {
                            jdbcTemplate.execute(
                                    "ALTER TABLE " + quote(tableName) + " ALTER COLUMN " + quote(columnName) + " VARCHAR(32)"
                            );
                        } catch (Exception ex) {
                            log.debug("Could not widen {}.{}: {}", tableName, columnName, ex.getMessage());
                        }
                    },
                    table,
                    column
            );
        } catch (Exception ex) {
            log.debug("Could not widen {}.{}: {}", table, column, ex.getMessage());
        }
    }

    private void makeColumnNullable(String table, String column) {
        try {
            jdbcTemplate.query(
                    """
                            SELECT TABLE_NAME, COLUMN_NAME
                            FROM INFORMATION_SCHEMA.COLUMNS
                            WHERE UPPER(TABLE_NAME) = ? AND UPPER(COLUMN_NAME) = ?
                            """,
                    rs -> {
                        String tableName = rs.getString("TABLE_NAME");
                        String columnName = rs.getString("COLUMN_NAME");
                        try {
                            jdbcTemplate.execute(
                                    "ALTER TABLE " + quote(tableName) + " ALTER COLUMN " + quote(columnName) + " SET NULL"
                            );
                        } catch (Exception ex) {
                            log.debug("Could not nullify {}.{}: {}", tableName, columnName, ex.getMessage());
                        }
                    },
                    table,
                    column
            );
        } catch (Exception ex) {
            log.debug("Could not nullify {}.{}: {}", table, column, ex.getMessage());
        }
    }

    private String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private record ConstraintRef(String name, String tableName) {
    }
}
