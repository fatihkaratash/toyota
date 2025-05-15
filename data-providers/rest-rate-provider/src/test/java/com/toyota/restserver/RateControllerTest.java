package com.toyota.restserver;

import com.toyota.restserver.controller.RateController;
import com.toyota.restserver.exception.RateNotFoundException;
import com.toyota.restserver.model.Rate;
import com.toyota.restserver.service.RateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.is;

@WebMvcTest(RateController.class)
class RateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RateService rateService;

    @Test
    void getRate_whenRateExists_shouldReturnRate() throws Exception {
        Rate mockRate = new Rate("PF2_USDTRY", 34.50, 34.55, "2024-07-15T10:00:00.000Z");
        given(rateService.getRateByName("PF2_USDTRY")).willReturn(mockRate);

        mockMvc.perform(get("/api/rates/PF2_USDTRY"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.pairName", is("PF2_USDTRY")))
                .andExpect(jsonPath("$.bid", is(34.50)))
                .andExpect(jsonPath("$.ask", is(34.55)));
    }

    @Test
    void getRate_whenRateDoesNotExist_shouldReturnNotFound() throws Exception {
        given(rateService.getRateByName("UNKNOWN")).willThrow(new RateNotFoundException("Rate not found for pair: UNKNOWN"));

        mockMvc.perform(get("/api/rates/UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message", is("Rate not found for pair: UNKNOWN")));
    }

    @Test
    void getRate_withLowerCasePathVariable_shouldConvertToUpperCaseAndReturnRate() throws Exception {
        Rate mockRate = new Rate("PF2_EURUSD", 1.0850, 1.0855, "2024-07-15T10:00:00.000Z");
        given(rateService.getRateByName("PF2_EURUSD")).willReturn(mockRate);

        mockMvc.perform(get("/api/rates/pf2_eurusd")) // lowercase
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.pairName", is("PF2_EURUSD")));
    }
}
