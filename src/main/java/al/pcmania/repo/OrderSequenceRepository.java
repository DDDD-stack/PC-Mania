package al.pcmania.repo;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Atomic per-year counter using MySQL's LAST_INSERT_ID(expr) trick. */
@Repository
public class OrderSequenceRepository {

    private final EntityManager em;

    public OrderSequenceRepository(EntityManager em) {
        this.em = em;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int next(int year) {
        em.createNativeQuery("""
                insert into order_sequence (seq_year, last_no) values (:y, last_insert_id(1))
                on duplicate key update last_no = last_insert_id(last_no + 1)""")
                .setParameter("y", year)
                .executeUpdate();
        return ((Number) em.createNativeQuery("select last_insert_id()").getSingleResult()).intValue();
    }
}
