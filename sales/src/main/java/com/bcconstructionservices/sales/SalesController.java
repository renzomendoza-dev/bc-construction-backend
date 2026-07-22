package com.bcconstructionservices.sales;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SalesController {

    @GetMapping("/api/sales/ping")
    public String ping() {
        return "Sales controller is alive!";
    }

}
