package com.shikhi.identity.web;

import com.shikhi.identity.security.AuthenticatedUser;
import com.shikhi.identity.service.AuthenticationService;
import com.shikhi.identity.service.TokenPair;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Public authentication endpoints (contract {@code /auth/*}). */
@RestController
@RequestMapping("/v1/auth")
public class AuthController {

	private final AuthenticationService authService;

	public AuthController(AuthenticationService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public TokenPair register(@Valid @RequestBody RegisterEmailRequest request) {
		return authService.register(request.email(), request.password(), request.displayName(),
				request.uiLocale());
	}

	@PostMapping("/login")
	public TokenPair login(@Valid @RequestBody LoginEmailRequest request) {
		return authService.login(request.email(), request.password());
	}

	@PostMapping("/refresh")
	public TokenPair refresh(@Valid @RequestBody RefreshRequest request) {
		return authService.refresh(request.refreshToken());
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUser principal) {
		authService.logout(principal.id());
		return ResponseEntity.noContent().build();
	}
}
