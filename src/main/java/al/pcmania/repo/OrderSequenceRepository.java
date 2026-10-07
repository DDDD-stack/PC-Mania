package al.pcmania.repo;

import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.jdbc.DatabaseDriver;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class OrderSequenceRepository {

    private final EntityManager em;
    private final boolean postgres;

    public OrderSequenceRepository(EntityManager em, DataSourceProperties dataSource) {
        this.em = em;
        this.postgres = DatabaseDriver.fromJdbcUrl(dataSource.determineUrl()) == DatabaseDriver.POSTGRESQL;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int next(int year) {
        return next("order_sequence", year);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int nextTrade(int year) {
        return next("trade_sequence", year);
    }

    private int next(String table, int year) {
        if (postgres) {

            return ((Number) em.createNativeQuery("""
                    insert into %1$s (seq_year, last_no) values (:y, 1)
                    on conflict (seq_year) do update set last_no = %1$s.last_no + 1
                    returning last_no""".formatted(table))
                    .setParameter("y", year)
                    .getSingleResult()).intValue();
        }

        em.createNativeQuery("""
                insert into %s (seq_year, last_no) values (:y, last_insert_id(1))
                on duplicate key update last_no = last_insert_id(last_no + 1)""".formatted(table))
                .setParameter("y", year)
                .executeUpdate();
        return ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).intValue();
    }
}
