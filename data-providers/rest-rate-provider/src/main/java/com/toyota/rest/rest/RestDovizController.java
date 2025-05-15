package com.toyota.provider.rest;

import com.toyota.provider.common.Doviz;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rates")
@Tag(name = "Rate Provider API", description = "Provides simulated currency exchange rates")
public class RestDovizController {

    private static final Logger logger = LoggerFactory.getLogger(RestDovizController.class);
    private final RestDovizService restDovizService;

    public RestDovizController(RestDovizService restDovizService) {
        this.restDovizService = restDovizService;
    }

    @Operation(summary = "Get the latest rate for a specific currency pair",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful retrieval of rate",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Doviz.class))),
                    @ApiResponse(responseCode = "404", description = "Rate pair not found or not available")
            })
    @GetMapping("/{rateName}")
    public ResponseEntity<Doviz> getRate(
            @Parameter(description = "Name of the currency pair (e.g., PF2_USDTRY)", required = true)
            @PathVariable String rateName) {
        logger.info("REST request for rate: {}", rateName);
        Doviz rate = restDovizService.getRateByName(rateName);
        if (rate == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity
                .ok()
                .cacheControl(CacheControl.noCache().noStore().mustRevalidate())
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .body(rate);
    }

    @Operation(summary = "Get the latest rates for all available currency pairs via REST",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful retrieval of all available rates",
                            content = @Content(mediaType = "application/json", schema = @Schema(type = "array", implementation = Doviz.class)))
            })
    @GetMapping("/all")
    public ResponseEntity<List<Doviz>> getAllLatestRates() {
        logger.info("REST request for all available rates.");
        List<String> availableRateNames = restDovizService.getAvailableRates();
        List<Doviz> latestRates = availableRateNames.stream()
                .map(restDovizService::getRateByName)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        return ResponseEntity
                .ok()
                .cacheControl(CacheControl.noCache().noStore().mustRevalidate())
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .body(latestRates);
    }

    @Operation(summary = "Manually trigger a refresh of a specific rate (for simulation/testing)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Rate refreshed successfully",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Doviz.class))),
                    @ApiResponse(responseCode = "404", description = "Rate pair not found or not available")
            })
    @PostMapping("/refresh/{rateName}")
    public ResponseEntity<Doviz> refreshRate(
            @Parameter(description = "Name of the currency pair to refresh", required = true)
            @PathVariable String rateName) {
        logger.info("REST request to refresh rate: {}", rateName);
        Doviz freshRate = restDovizService.generateFreshRate(rateName);
        if (freshRate == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(freshRate);
    }
}
