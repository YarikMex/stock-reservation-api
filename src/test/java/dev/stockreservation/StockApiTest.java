package dev.stockreservation;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class StockApiTest {
    @Autowired MockMvc api;
    @Autowired JdbcClient jdbc;
    @Autowired ReservationService service;
    @MockitoSpyBean ReservationRepository reservations;

    private String sku;

    @BeforeEach
    void insertProduct() {
        sku = "TEST-" + UUID.randomUUID();
        jdbc.sql("INSERT INTO product (sku, name, available) VALUES (:sku, 'Test product', 1)")
                .param("sku", sku)
                .update();
    }

    @AfterEach
    void deleteProduct() {
        jdbc.sql("DELETE FROM reservation WHERE sku = :sku").param("sku", sku).update();
        jdbc.sql("DELETE FROM product WHERE sku = :sku").param("sku", sku).update();
    }

    @Test
    void onlyOneRequestCanReserveTheLastUnit() throws Exception {
        var start = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return reserve("1");
            });
            var second = workers.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return reserve("1");
            });
            var responses = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(responses)
                    .extracting(response -> response.getResponse().getStatus())
                    .containsExactlyInAnyOrder(201, 409);
        }

        api.perform(get("/api/products/{sku}", sku))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(0));
        assertThat(jdbc.sql("SELECT count(*) FROM reservation WHERE sku = :sku")
                .param("sku", sku).query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void cancellationRestoresStockOnlyOnce() throws Exception {
        var created = reserve("1").getResponse();
        assertThat(created.getStatus()).isEqualTo(201);
        String reservation = created.getHeader("Location");
        assertThat(reservation).startsWith("/api/reservations/");
        api.perform(get(reservation))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value(sku));

        String first = api.perform(put(reservation + "/cancellation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledAt").isString())
                .andReturn().getResponse().getContentAsString();

        api.perform(put(reservation + "/cancellation"))
                .andExpect(status().isOk())
                .andExpect(content().json(first));
        assertThat(service.product(sku).available()).isEqualTo(1);
    }

    @Test
    void rejectsInvalidAndMissingValues() throws Exception {
        assertThat(reserve("0").getResponse().getStatus()).isEqualTo(400);
        assertThat(reserve("-1").getResponse().getStatus()).isEqualTo(400);
        assertThat(reserve("1.5").getResponse().getStatus()).isEqualTo(400);
        assertThat(reserve("null").getResponse().getStatus()).isEqualTo(400);
        api.perform(get("/api/products/DOES-NOT-EXIST")).andExpect(status().isNotFound());
        api.perform(put("/api/reservations/{id}/cancellation", UUID.randomUUID()))
                .andExpect(status().isNotFound());
        assertThat(service.product(sku).available()).isEqualTo(1);
    }

    @Test
    void reservationWriteFailureRollsBackTheStockChange() {
        doThrow(new IllegalStateException("Simulated reservation write failure"))
                .when(reservations).insert(any(UUID.class), eq(sku), eq(1));

        assertThatThrownBy(() -> service.reserve(sku, 1))
                .isInstanceOf(IllegalStateException.class);
        assertThat(service.product(sku).available()).isEqualTo(1);
    }

    private MvcResult reserve(String quantity) throws Exception {
        return api.perform(post("/api/reservations")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"sku": "%s", "quantity": %s}
                                """.formatted(sku, quantity)))
                .andReturn();
    }
}
