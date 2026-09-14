package com.dozernet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * One-time backward-compatibility migration for databases created before
 * {@code User.role} (a single scalar column) became {@code User.roles} (a
 * {@code user_roles} collection table).
 *
 * <p>Hibernate's {@code ddl-auto=update} adds the new {@code user_roles} table
 * but never migrates data into it, and it never drops the old {@code role}
 * column. On a fresh H2 database (which is recreated from scratch every boot)
 * this never applies; on a persisted MySQL database created by an earlier
 * version of the app, every account would otherwise end up with zero granted
 * authorities - effectively locked out, admins included.</p>
 *
 * <p>Runs after {@link DataSeeder} (which only fires on an empty database) and
 * is safe to run on every boot: it only touches users whose legacy role has
 * not yet been copied across, so it is a no-op once the one-time backfill has
 * happened.</p>
 */
@Component
@Order(2)
public class RoleMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RoleMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public RoleMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        if (!legacyRoleColumnExists()) {
            return;
        }

        List<Object[]> missing = new ArrayList<>();
        jdbcTemplate.query("""
                select u.id, u.role
                from users u
                where u.role is not null
                  and not exists (select 1 from user_roles r where r.user_id = u.id)
                """, (ResultSet rs) -> {
            missing.add(new Object[]{rs.getLong("id"), rs.getString("role")});
        });

        if (missing.isEmpty()) {
            return;
        }

        for (Object[] row : missing) {
            jdbcTemplate.update("insert into user_roles (user_id, role) values (?, ?)", row[0], row[1]);
        }
        log.info("Backfilled user_roles for {} account(s) migrated from the old single-role column", missing.size());
    }

    /**
     * The legacy {@code role} column only exists on a database created before
     * the multi-role change; a fresh schema never has it. Checked defensively
     * so this migration is a safe no-op on every other database.
     */
    private boolean legacyRoleColumnExists() {
        try {
            jdbcTemplate.queryForObject("select role from users limit 1", String.class);
            return true;
        } catch (org.springframework.dao.EmptyResultDataAccessException emptyTable) {
            // Table exists and has the column, just no rows yet - still needs checking on future rows.
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
