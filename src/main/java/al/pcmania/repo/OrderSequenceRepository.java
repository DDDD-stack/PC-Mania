package al.pcmania.repo;

import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.jdbc.DatabaseDriver;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomic per-year counters behind the order numbers (PM-2026-0001) and trade-in requests (TR-2026-0001).
 *
 * The upsert is vendor-specific, and it has to be: the live shop runs on Postgres (Supabase), while a
 * local install may still use MySQL. The MySQL statement alone is a syntax error on Postgres, which made
 * every checkout on the hosted site fail with a 500.
 */
@Repository
public class OrderSequenceRepository {

    private final EntityManager em;
    private final boolean postgres;

    public OrderSequenceRepository(EntityManager em, DataSourceProperties dataSource) {
        this.em = em;
        this.postgres = DatabaseDriver.fromJdbcUrl(dataSource.determineUrl()) == DatabaseDriver.POSTGRESQL;
    }

    /** Order numbers, PM-2026-0001. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int next(int year) {
        return next("order_sequence", year);
    }

    /** Trade-in request numbers, TR-2026-0001: their own counter, so order numbers stay gap-free. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int nextTrade(int year) {
        return next("trade_sequence", year);
    }

    /** {@code table} is one of the two constants above, never caller input: table names cannot be bound. */
    private int next(String table, int year) {
        if (postgres) {
            // The row lock taken by ON CONFLICT serialises concurrent requests; RETURNING hands back this one's number.
            return ((Number) em.createNativeQuery("""
                    insert into %1$s (seq_year, last_no) values (:y, 1)
                    on conflict (seq_year) do update set last_no = %1$s.last_no + 1
                    returning last_no""".formatted(table))
                    .setParameter("y", year)
                    .getSingleResult()).intValue();
        }
        // MySQL: LAST_INSERT_ID(expr) stores the new value for this connection only.
        em.createNativeQuery("""
                insert into %s (seq_year, last_no) values (:y, last_insert_id(1))
                on duplicate key update last_no = last_insert_id(last_no + 1)""".formatted(table))
                .setParameter("y", year)
                .executeUpdate();
        return ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).intValue();
    }
}
