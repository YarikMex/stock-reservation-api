package dev.stockreservation;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class StockController {
    private final ReservationService service;

    StockController(ReservationService service) {
        this.service = service;
    }

    @GetMapping("/products/{sku}")
    Product product(@PathVariable String sku) {
        return service.product(sku);
    }

    @GetMapping("/reservations/{id}")
    Reservation reservation(@PathVariable UUID id) {
        return service.reservation(id);
    }

    @PostMapping("/reservations")
    ResponseEntity<Reservation> reserve(@Valid @RequestBody ReserveRequest request) {
        Reservation reservation = service.reserve(request.sku(), request.quantity());
        return ResponseEntity.created(URI.create("/api/reservations/" + reservation.id())).body(reservation);
    }

    @PutMapping("/reservations/{id}/cancellation")
    Reservation cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }

    record ReserveRequest(
            @NotBlank @Size(max = 64) String sku,
            @NotNull @Positive Integer quantity) {}
}
