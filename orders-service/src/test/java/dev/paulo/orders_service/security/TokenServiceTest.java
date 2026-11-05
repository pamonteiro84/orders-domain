package dev.paulo.orders_service.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import dev.paulo.orders_service.user.domain.User;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class TokenServiceTest {

    @Test
    void issue_returnsSignedTokenWithUserIdIssuerAndExpiration() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keys.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keys.getPrivate();

        RSAKey jwk = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        TokenService service = new TokenService(encoder, Duration.ofMinutes(15));
        User ana = new User("ana@x.pt", "hash");

        String token = service.issue(ana);

        JwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        Jwt decoded = decoder.decode(token);
        assertThat(decoded.getSubject()).isEqualTo(ana.getId().toString());
        assertThat(decoded.getClaimAsString("iss")).isEqualTo("orders-service");
        assertThat(Duration.between(decoded.getIssuedAt(), decoded.getExpiresAt()))
                .isEqualTo(Duration.ofMinutes(15));
        assertThat(decoded.getClaims()).doesNotContainKeys("email", "password", "passwordHash");
    }
}
