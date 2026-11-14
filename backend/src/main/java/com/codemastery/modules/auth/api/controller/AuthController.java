package com.codemastery.modules.auth.api.controller;

import com.codemastery.modules.auth.api.dto.AuthRequests;
import com.codemastery.modules.auth.api.dto.AuthRequests.LoginRequest;
import com.codemastery.modules.auth.api.dto.AuthRequests.PasswordResetConfirmRequest;
import com.codemastery.modules.auth.api.dto.AuthRequests.RegisterRequest;
import com.codemastery.modules.auth.api.dto.AuthRequests.PasswordResetRequest;
import com.codemastery.modules.auth.api.dto.AuthResponse;
import com.codemastery.modules.auth.api.dto.AuthResponse.PasswordResetAcknowledgement;
import com.codemastery.modules.auth.api.dto.AuthResponse.PasswordResetCompleted;
import com.codemastery.modules.auth.api.dto.UserResponse;
import com.codemastery.modules.auth.application.service.AuthService;
import com.codemastery.modules.auth.application.service.PasswordResetService;
import com.codemastery.modules.auth.infrastructure.security.JwtTokenProvider;
import jakarta.validation.Valid;
import org.springframework.core.MethodParameter;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    private final JwtTokenProvider tokens;

    private final PasswordResetService passwordResets;

    public AuthController(AuthService auth, JwtTokenProvider tokens, PasswordResetService passwordResets) {
        this.auth = auth;
        this.tokens = tokens;
        this.passwordResets = passwordResets;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        var user = auth.authenticate(request.email(), request.password());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new AuthResponse(tokens.createToken(user.id()), "Bearer", 900, UserResponse.from(user)));
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse result = UserResponse.from(auth.register(request.email(), request.password(), request.displayName()));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(result);
    }

    /** CT02-REST: JSON-only public request; other media types are rejected with CT18 415. */
    @PostMapping(path = "/password-reset-requests", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PasswordResetAcknowledgement> requestPasswordReset(@RequestBody String json) {
        PasswordResetRequest request = AuthRequests.parsePasswordResetRequest(json);
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore())
                .body(new PasswordResetAcknowledgement(
                        passwordResets.requestReset(request.email()).message()));
    }

    /** CT02-REST: JSON-only public confirmation; never authenticates or issues a JWT. */
    @PostMapping(path = "/password-resets", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PasswordResetCompleted> confirmPasswordReset(@RequestBody String json) {
        PasswordResetConfirmRequest request = AuthRequests.parsePasswordResetConfirmRequest(json);
        String message = passwordResets.resetPassword(request.token(), request.password()).message();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new PasswordResetCompleted(message));
    }

    /** Also covers shared MVC advice responses before writing register error bodies. */
    @ControllerAdvice
    public static class RegistrationCacheAdvice implements ResponseBodyAdvice<Object> {
        @Override
        public boolean supports(MethodParameter returnType,
                                Class<? extends HttpMessageConverter<?>> converterType) {
            return true;
        }

        @Override
        public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                      Class<? extends HttpMessageConverter<?>> converterType,
                                      ServerHttpRequest request, ServerHttpResponse response) {
            String path = request.getURI().getPath();
            if ("/api/auth/register".equals(path) || "/api/auth/login".equals(path)
                    || "/api/auth/password-reset-requests".equals(path)
                    || "/api/auth/password-resets".equals(path)) {
                response.getHeaders().setCacheControl("no-store");
            }
            return body;
        }
    }
}
