package com.ruler.one.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * Minimal helper to bind/read JSON columns across databases.
 * <p>
 * - Postgres: prefer binding as typed jsonb via PGobject (avoids SQL ::jsonb casts).
 * - Others (H2 tests): bind as plain String.
 */
public final class DbJson {
    private DbJson() {}

    public static void setJsonbOrString(PreparedStatement ps, int index, String json) {
        try {
            Connection c = ps.getConnection();
            String product = c.getMetaData().getDatabaseProductName();
            if (product != null && product.toLowerCase().contains("postgres")) {
                // Avoid referencing PGobject type at compile time via reflection.
                Object pg = Class.forName("org.postgresql.util.PGobject").getDeclaredConstructor().newInstance();
                pg.getClass().getMethod("setType", String.class).invoke(pg, "jsonb");
                pg.getClass().getMethod("setValue", String.class).invoke(pg, json);
                ps.setObject(index, pg);
            } else {
                ps.setString(index, json);
            }
        } catch (Exception e) {
            // Fallback: best-effort string binding
            try {
                ps.setString(index, json);
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to bind json parameter", ex);
            }
        }
    }
}

