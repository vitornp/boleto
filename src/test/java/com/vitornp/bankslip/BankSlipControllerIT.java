package com.vitornp.bankslip;

import com.vitornp.bankslip.model.BankSlip;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureMockMvc
public class BankSlipControllerIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BankSlipService bankSlipService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM bank_slip");
    }

    @Test
    public void createSuccessfully() throws Exception {
        // Given
        String request = "{" +
            "  \"due_date\": \"2020-01-01\"," +
            "  \"customer\": \"Test\"," +
            "  \"total_in_cents\": 0.1" +
            "}";

        // When
        ResultActions resultActions = this.mvc.perform(
            post("/bankslips")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
        );

        // Then
        resultActions
            .andExpect(status().isCreated())
            .andExpect(jsonPath("id", notNullValue()))
            .andExpect(jsonPath("due_date", equalTo("2020-01-01")))
            .andExpect(jsonPath("payment_date").doesNotExist())
            .andExpect(jsonPath("total_in_cents", equalTo(0.1)))
            .andExpect(jsonPath("customer", equalTo("Test")))
            .andExpect(jsonPath("status", equalTo("PENDING")))
            .andExpect(jsonPath("fine").doesNotExist());
    }

    @Test
    public void createErrorWhenAnyFieldIsNull() throws Exception {
        // Given
        String request = "{" +
            "  \"customer\": \"Test\"," +
            "  \"total_in_cents\": 0.1" +
            "}";

        // When
        ResultActions resultActions = this.mvc.perform(
            post("/bankslips")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
        );

        // Then
        resultActions
            .andDo(print())
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("error", equalTo(422)))
            .andExpect(jsonPath("status", equalTo("Unprocessable Entity")))
            .andExpect(jsonPath("$.errors.due_date", startsWith("must not be null")));
    }

    @Test
    public void findAll() throws Exception {
        // Given
        LocalDate dueDate = LocalDate.now();
        BankSlip bankSlipPayment = givenBankSlip(dueDate.plusDays(3), "Test 3", "3000");
        BankSlip bankSlipCanceled = givenBankSlip(dueDate.plusDays(4), "Test 4", "4000");
        bankSlipService.save(bankSlipPayment);
        bankSlipService.save(bankSlipCanceled);
        bankSlipService.save(givenBankSlip(dueDate.plusDays(2), "Test 1", "1000"));
        bankSlipService.save(givenBankSlip(dueDate.plusDays(1), "Test 2", "2000"));

        bankSlipService.paymentById(bankSlipPayment.getId(), LocalDate.now().plusDays(4));
        bankSlipService.cancelById(bankSlipCanceled.getId());

        // When
        ResultActions resultActions = this.mvc.perform(
            get("/bankslips")
                .contentType(MediaType.APPLICATION_JSON)
        );

        // Then
        resultActions
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.*", hasSize(4)))

            .andExpect(jsonPath("$[0].id", notNullValue()))
            .andExpect(jsonPath("$[0].due_date", equalTo(dueDate.plusDays(1).toString())))
            .andExpect(jsonPath("$[0].payment_date").doesNotExist())
            .andExpect(jsonPath("$[0].total_in_cents", equalTo(2000.0)))
            .andExpect(jsonPath("$[0].customer", equalTo("Test 2")))
            .andExpect(jsonPath("$[0].status", equalTo("PENDING")))
            .andExpect(jsonPath("$[0].fine").doesNotExist())

            .andExpect(jsonPath("$[1].id", notNullValue()))
            .andExpect(jsonPath("$[1].due_date", equalTo(dueDate.plusDays(2).toString())))
            .andExpect(jsonPath("$[1].payment_date").doesNotExist())
            .andExpect(jsonPath("$[1].total_in_cents", equalTo(1000.0)))
            .andExpect(jsonPath("$[1].customer", equalTo("Test 1")))
            .andExpect(jsonPath("$[1].status", equalTo("PENDING")))
            .andExpect(jsonPath("$[1].fine").doesNotExist())

            .andExpect(jsonPath("$[2].id", notNullValue()))
            .andExpect(jsonPath("$[2].due_date", equalTo(dueDate.plusDays(3).toString())))
            .andExpect(jsonPath("$[2].payment_date", equalTo(LocalDate.now().plusDays(4).toString())))
            .andExpect(jsonPath("$[2].total_in_cents", equalTo(3000.0)))
            .andExpect(jsonPath("$[2].customer", equalTo("Test 3")))
            .andExpect(jsonPath("$[2].status", equalTo("PAID")))
            .andExpect(jsonPath("$[2].fine").doesNotExist())

            .andExpect(jsonPath("$[3].id", notNullValue()))
            .andExpect(jsonPath("$[3].due_date", equalTo(dueDate.plusDays(4).toString())))
            .andExpect(jsonPath("$[3].payment_date").doesNotExist())
            .andExpect(jsonPath("$[3].total_in_cents", equalTo(4000.0)))
            .andExpect(jsonPath("$[3].customer", equalTo("Test 4")))
            .andExpect(jsonPath("$[3].status", equalTo("CANCELED")))
            .andExpect(jsonPath("$[3].fine").doesNotExist());
    }

    @Test
    public void findAllWhenEmpty() throws Exception {
        // Given

        // When
        ResultActions resultActions = this.mvc.perform(
            get("/bankslips")
                .contentType(MediaType.APPLICATION_JSON)
        );

        // Then
        resultActions
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.*", hasSize(0)));
    }

    @Test
    public void findById() throws Exception {
        // Given
        LocalDate dueDate = LocalDate.now();
        LocalDate paymentDate = LocalDate.now().plusDays(1);
        BankSlip bankSlip = givenBankSlip(dueDate, "Test 1", "2000");
        bankSlipService.save(bankSlip);
        bankSlipService.paymentById(bankSlip.getId(), paymentDate);

        // When
        ResultActions resultActions = this.mvc.perform(
            get(String.format("/bankslips/%s", bankSlip.getId()))
                .contentType(MediaType.APPLICATION_JSON)
        );

        // Then
        resultActions
            .andExpect(status().isOk())
            .andExpect(jsonPath("id", notNullValue()))
            .andExpect(jsonPath("due_date", equalTo(dueDate.toString())))
            .andExpect(jsonPath("payment_date", equalTo(paymentDate.toString())))
            .andExpect(jsonPath("total_in_cents", equalTo(2000.0)))
            .andExpect(jsonPath("customer", equalTo("Test 1")))
            .andExpect(jsonPath("status", equalTo("PAID")))
            .andExpect(jsonPath("fine", equalTo(10.0)));
    }

    @Test
    public void paymentById() throws Exception {
        // Given
        BankSlip bankSlip = bankSlipService.save(givenBankSlip(LocalDate.now().plusDays(2), "Test 1", "1000"));
        String request = "{" +
            "  \"payment_date\": \"2020-01-01\"" +
            "}";

        // When
        ResultActions resultActions = this.mvc.perform(
            post(String.format("/bankslips/%s/payments", bankSlip.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
        );

        // Then
        resultActions
            .andExpect(status().isNoContent());
    }

    @Test
    public void paymentByIdWhenNotFound() throws Exception {
        // Given
        String request = "{" +
            "  \"payment_date\": \"2020-01-01\"" +
            "}";

        // When
        ResultActions resultActions = this.mvc.perform(
            post(String.format("/bankslips/%s/payments", UUID.randomUUID()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
        );

        // Then
        resultActions
            .andExpect(status().isNotFound());
    }

    @Test
    public void cancelById() throws Exception {
        // Given
        BankSlip bankSlip = bankSlipService.save(givenBankSlip(LocalDate.now().plusDays(2), "Test 1", "1000"));

        // When
        ResultActions resultActions = this.mvc.perform(
            delete(String.format("/bankslips/%s", bankSlip.getId()))
                .contentType(MediaType.APPLICATION_JSON)
        );

        // Then
        resultActions
            .andExpect(status().isNoContent());
    }

    @Test
    public void cancelByIdWhenNotFound() throws Exception {
        // Given

        // When
        ResultActions resultActions = this.mvc.perform(
            delete(String.format("/bankslips/%s", UUID.randomUUID()))
                .contentType(MediaType.APPLICATION_JSON)
        );

        // Then
        resultActions
            .andExpect(status().isNotFound());
    }

    private BankSlip givenBankSlip(LocalDate dueDate, String customer, String totalInCents) {
        return BankSlip.builder()
            .dueDate(dueDate)
            .customer(customer)
            .totalInCents(new BigDecimal(totalInCents))
            .build();
    }
}
