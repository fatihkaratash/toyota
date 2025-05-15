package com.toyota.restserver.controller;

import com.toyota.restserver.logging.LoggingHelper;
import com.toyota.restserver.model.Rate;
import com.toyota.restserver.service.RateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rates")
public class RateController {

    private static final LoggingHelper log = new LoggingHelper(RateController.class);
    private final RateService rateService;

    public RateController(RateService rateService) {
        this.rateService = rateService;
    }

    @GetMapping("/{rateName}")
    public ResponseEntity<Rate> getRate(@PathVariable String rateName) {
        String upperCaseRateName = rateName.toUpperCase();
        log.info(LoggingHelper.OPERATION_REQUEST, LoggingHelper.PLATFORM_REST, upperCaseRateName, null, 
                "Kur bilgisi icin istek alindi.");
        Rate rate = rateService.getRateByName(upperCaseRateName);
        log.info(LoggingHelper.OPERATION_RESPONSE, LoggingHelper.PLATFORM_REST, upperCaseRateName,
                String.format("BID:%.5f ASK:%.5f", rate.getBid(), rate.getAsk()),
                "Kur bilgisi yaniti gonderiliyor.");
        return ResponseEntity.ok(rate);
    }
}
