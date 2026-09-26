package in.hundredmph.api.admin;

import in.hundredmph.api.admin.dto.CreateUserRequest;
import in.hundredmph.api.admin.dto.SetPasswordRequest;
import in.hundredmph.api.admin.dto.SetStatusRequest;
import in.hundredmph.api.me.dto.UserDto;
import in.hundredmph.api.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The back office (README §5.10). The whole tree is admin-only — enforced in
 * SecurityConfig on the path, so a new handler here cannot be added unguarded.
 */
@RestController
@RequestMapping("/v1/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /** Backs app/admin/create-user.tsx — this is how members get in. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(@Valid @RequestBody CreateUserRequest request) {
        return adminUserService.create(request);
    }

    /** Backs app/(tabs)/clients.tsx. */
    @GetMapping
    public List<UserDto> roster() {
        return adminUserService.roster();
    }

    @PatchMapping("/{userId}/status")
    public UserDto setStatus(@AuthenticationPrincipal AuthPrincipal principal,
                             @PathVariable String userId,
                             @Valid @RequestBody SetStatusRequest request) {
        return adminUserService.setStatus(principal.userId(), userId, request.status());
    }

    /** The only way back in for a client who forgot their password: email reset is not wired yet. */
    @PutMapping("/{userId}/password")
    public ResponseEntity<Void> setPassword(@PathVariable String userId,
                                            @Valid @RequestBody SetPasswordRequest request) {
        adminUserService.setPassword(userId, request.password());
        return ResponseEntity.noContent().build();
    }
}
