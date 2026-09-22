package al.pcmania.repo;

import al.pcmania.domain.Enums.OrderStatus;
import al.pcmania.domain.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);
    long countByStatus(OrderStatus status);

    Page<Order> findByStatusInOrderByCreatedAtDesc(java.util.Collection<OrderStatus> statuses, Pageable pageable);

    Optional<Order> findTopByOrderByIdDesc();

    @Query("select distinct o from Order o left join fetch o.items i left join fetch i.product where o.id = :id")
    Optional<Order> findWithItems(Long id);

    @Query("select distinct o from Order o left join fetch o.items i left join fetch i.product where o.status in :statuses")
    List<Order> findWithItemsByStatusIn(List<OrderStatus> statuses);
}
