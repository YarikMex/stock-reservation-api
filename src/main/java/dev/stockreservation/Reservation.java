package dev.stockreservation;

import java.time.OffsetDateTime;
import java.util.UUID;

record Reservation(UUID id, String sku, int quantity, OffsetDateTime createdAt, OffsetDateTime cancelledAt) {}
