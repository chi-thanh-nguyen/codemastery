package com.codemastery.modules.auth.api.controller;

import com.codemastery.modules.auth.api.dto.AuthRequests.RegisterRequest;
import com.codemastery.modules.auth.api.dto.UserResponse;
import com.codemastery.modules.auth.api.dto.AuthResponse;
import com.codemastery.modules.auth.api.dto.AuthRequests.LoginRequest;
import com.codemastery.modules.auth.infrastructure.security.JwtTokenProvider;
import com.codemastery.modules.auth.application.service.AuthService;
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

    public AuthController(AuthService auth, JwtTokenProvider tokens) {
        this.auth = auth; this.tokens = tokens;
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
            if ("/api/auth/register".equals(path) || "/api/auth/login".equals(path)) {
                response.getHeaders().setCacheControl("no-store");
            }
            return body;
        }
    }
}
