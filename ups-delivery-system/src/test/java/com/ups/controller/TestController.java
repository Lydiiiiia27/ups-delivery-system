package com.ups.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
@Profile("test")
public class TestController {

    @GetMapping("/tracking")
    public ResponseEntity<Map<String, Object>> trackPackage(@RequestParam("trackingNumber") Long trackingNumber) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("trackingNumber", trackingNumber);
        response.put("status", "CREATED");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("token", "test-token");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/complete-flow")
    public ResponseEntity<String> completeFlow() {
        return ResponseEntity.ok("Complete flow test executed successfully");
    }

    @PostMapping("/redirect-package")
    public ResponseEntity<Map<String, Object>> redirectPackage() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }
}