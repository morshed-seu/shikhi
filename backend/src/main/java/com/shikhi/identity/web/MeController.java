package com.shikhi.identity.web;

import com.shikhi.identity.security.AuthenticatedUser;
import com.shikhi.identity.service.AccountService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated profile endpoints (contract {@code /me}, {@code /me/identities}). */
@RestController
@RequestMapping("/v1/me")
public class MeController {

	private final AccountService accountService;

	public MeController(AccountService accountService) {
		this.accountService = accountService;
	}

	@GetMapping
	public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
		return UserResponse.from(accountService.getUser(principal.id()));
	}

	@PatchMapping
	public UserResponse updateProfile(@AuthenticationPrincipal AuthenticatedUser principal,
			@RequestBody UpdateProfileRequest request) {
		return UserResponse.from(accountService.updateProfile(principal.id(),
				request.displayName(), request.uiLocale()));
	}

	@GetMapping("/identities")
	public List<IdentityResponse> identities(
			@AuthenticationPrincipal AuthenticatedUser principal) {
		return accountService.listIdentities(principal.id()).stream()
				.map(IdentityResponse::from)
				.toList();
	}
}
