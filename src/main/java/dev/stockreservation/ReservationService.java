package dev.stockreservation;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class ReservationService {
    private final InventoryRepository inventory;
    private final ReservationRepository reservations;

    ReservationService(InventoryRepository inventory, ReservationRepository reservations) {
        this.inventory = inventory;
        this.reservations = reservations;
    }

    Product product(String sku) {
        return inventory.find(sku).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
    }

    Reservation reservation(UUID id) {
        return reservations.find(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found."));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Reservation reserve(String sku, int quantity) {
        if (!inventory.allocate(sku, quantity)) {
            product(sku);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient available stock.");
        }

        return reservations.insert(UUID.randomUUID(), sku, quantity);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Reservation cancel(UUID id) {
        var cancelled = reservations.cancelActive(id);
        if (cancelled.isEmpty()) {
            return reservation(id);
        }

        Reservation reservation = cancelled.get();
        inventory.release(reservation.sku(), reservation.quantity());
        return reservation;
    }
}
