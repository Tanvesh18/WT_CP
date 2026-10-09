package com.tripshield.backend.security;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/travelers")
public class TravelerController {
    private final UserRepository users;
    private final Access access;
    public TravelerController(UserRepository users, Access access) { this.users = users; this.access = access; }
    public record Traveler(Long id, String name, String email) {}
    @GetMapping
    public List<Traveler> list() {
        access.coordinator();
        return users.findAll().stream().filter(user -> "TRAVELER".equals(user.role))
                .map(user -> new Traveler(user.id, user.name, user.email))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name())).toList();
    }
}
