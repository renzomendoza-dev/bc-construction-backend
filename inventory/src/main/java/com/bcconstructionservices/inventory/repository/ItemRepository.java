package com.bcconstructionservices.inventory.repository;

import com.bcconstructionservices.inventory.entity.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {

    Optional<Item> findBySku(String sku);

    boolean existsBySku(String sku);

    /**
     * Filters items by optional category, active status, and a case-insensitive
     * partial match against name or sku. Any null parameter is ignored.
     */
    @Query("""
            SELECT i FROM Item i
            WHERE (:category IS NULL OR i.category = :category)
              AND (:active IS NULL OR i.active = :active)
              AND (:search IS NULL
                   OR LOWER(i.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                   OR LOWER(i.sku) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
            """)
    Page<Item> search(@Param("category") String category,
                       @Param("active") Boolean active,
                       @Param("search") String search,
                       Pageable pageable);
}
