package dev.stockreservation;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ReservationRepository {
    private final JdbcClient jdbc;

    ReservationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Reservation insert(UUID id, String sku, int quantity) {
        return jdbc.sql("""
                INSERT INTO reservation (id, sku, quantity) VALUES (:id, :sku, :quantity)
                RETURNING id, sku, quantity, created_at, cancelled_at
                """)
                .param("id", id)
                .param("sku", sku)
                .param("quantity", quantity)
                .query(Reservation.class)
                .single();
    }

    Optional<Reservation> find(UUID id) {
        return jdbc.sql("""
                SELECT id, sku, quantity, created_at, cancelled_at FROM reservation WHERE id = :id
                """)
                .param("id", id)
                .query(Reservation.class)
                .optional();
    }

    Optional<Reservation> cancelActive(UUID id) {
        return jdbc.sql("""
                UPDATE reservation SET cancelled_at = statement_timestamp()
                WHERE id = :id AND cancelled_at IS NULL
                RETURNING id, sku, quantity, created_at, cancelled_at
                """)
                .param("id", id)
                .query(Reservation.class)
                .optional();
    }
}
