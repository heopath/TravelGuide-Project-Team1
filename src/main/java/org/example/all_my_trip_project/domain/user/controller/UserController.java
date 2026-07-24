package org.example.all_my_trip_project.domain.user.controller;

import lombok.RequiredArgsConstructor;
import org.example.all_my_trip_project.domain.user.dto.UserDTO;
import org.example.all_my_trip_project.domain.user.service.UserService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@Profile("!ui")
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserDTO> create(@RequestBody UserDTO user) {
        Long id = userService.create(user);
        return ResponseEntity.created(URI.create("/api/users/" + id)).body(userService.get(id));
    }

    @GetMapping("/{userId}")
    public UserDTO get(@PathVariable Long userId) {
        return userService.get(userId);
    }

    @GetMapping
    public List<UserDTO> getAll() {
        return userService.getAll();
    }

    @PutMapping("/{userId}")
    public UserDTO update(@PathVariable Long userId, @RequestBody UserDTO user) {
        user.setUserId(userId);
        userService.update(user);
        return userService.get(userId);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> withdraw(@PathVariable Long userId) {
        userService.withdraw(userId);
        return ResponseEntity.noContent().build();
    }
}
