package com.shikhi.platform.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contract-facing liveness/readiness endpoints (see docs/43-api-contract.openapi.yaml,
 * paths /health and /ready under the /v1 base). Operational-grade health is also exposed
 * via Spring Boot Actuator (/actuator/health) for the load balancer / orchestrator.
 *
 * <p>M0 walking skeleton: these return static UP/READY. In M1+ the readiness check will
 * verify downstream dependencies (database, cache) before reporting READY.
 */
@RestController
@RequestMapping("/v1")
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "shikhi");
    }

    @GetMapping("/ready")
    public Map<String, String> ready() {
        return Map.of("status", "READY");
    }
}
