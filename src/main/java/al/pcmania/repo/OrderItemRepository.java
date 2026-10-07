package al.pcmania.repo;

import al.pcmania.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    boolean existsByProductId(Long productId);

    @Query("""
            select count(i) > 0 from OrderItem i
            where i.product.id = :productId and i.order.id <> :orderId
              and i.order.status in (al.pcmania.domain.Enums.OrderStatus.NEW, al.pcmania.domain.Enums.OrderStatus.CONFIRMED, al.pcmania.domain.Enums.OrderStatus.SHIPPED)""")
    boolean existsOpenForProduct(@Param("productId") Long productId, @Param("orderId") Long orderId);

    @Query("select i from OrderItem i join fetch i.order o left join fetch i.product where o.id in :orderIds")
    List<OrderItem> findByOrderIds(@Param("orderIds") List<Long> orderIds);
}
