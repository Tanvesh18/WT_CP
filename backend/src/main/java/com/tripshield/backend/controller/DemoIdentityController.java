package com.tripshield.backend.controller;

import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes a safe identity check so seed/reset tools cannot target another profile. */
@RestController
@Profile("demo")
public class DemoIdentityController {
    @GetMapping("/api/demo/identity")
    public Map<String, String> identity() {
        return Map.of("mode", "DEMO", "database", "tripshield_demo");
    }
}
