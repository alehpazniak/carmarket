package com.carmarket.admin.system;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/system")
@RequiredArgsConstructor
public class SystemController {

    private final SystemHealthService healthService;

    @GetMapping("/health")
    public SystemHealthService.SystemHealth health() {
        return healthService.check();
    }
}
