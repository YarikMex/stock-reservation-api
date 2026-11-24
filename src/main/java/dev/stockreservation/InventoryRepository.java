package dev.stockreservation;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class InventoryRepository {
    private final JdbcClient jdbc;

    InventoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Product> find(String sku) {
        return jdbc.sql("SELECT sku, name, available FROM product WHERE sku = :sku")
                .param("sku", sku)
                .query(Product.class)
                .optional();
    }

    boolean allocate(String sku, int quantity) {
        return jdbc.sql("""
                UPDATE product SET available = available - :quantity
                WHERE sku = :sku AND available >= :quantity
                """)
                .param("sku", sku)
                .param("quantity", quantity)
                .update() == 1;
    }

    void release(String sku, int quantity) {
        jdbc.sql("UPDATE product SET available = available + :quantity WHERE sku = :sku")
                .param("sku", sku)
                .param("quantity", quantity)
                .update();
    }
}
