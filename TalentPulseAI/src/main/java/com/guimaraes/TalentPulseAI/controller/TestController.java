package com.guimaraes.TalentPulseAI.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping ("/api")
public class TestController {

    @GetMapping ("/public/ping")
    public ResponseEntity<String> publicPing() {
        return ResponseEntity.ok("Public endpoint is working!");
    }
    
    @GetMapping ("/protected/ping")
    public ResponseEntity<String> protectedPing() {
        return ResponseEntity.ok("Authenticated JWT validation working!");
    }

    @GetMapping("/recruiter/ping")
    public ResponseEntity<String> recruiterPing() {
        return ResponseEntity.ok("Recruiter role authorization working!");
    }
}