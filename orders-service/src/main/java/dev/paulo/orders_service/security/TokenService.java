package dev.paulo.orders_service.security;

import dev.paulo.orders_service.user.domain.User;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final Duration expiration;

    public TokenService(JwtEncoder encoder, @Value("${jwt.expiration}") Duration expiration) {
        this.encoder = encoder;
        this.expiration = expiration;
    }

    public String issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("orders-service")
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(expiration))
                .build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
