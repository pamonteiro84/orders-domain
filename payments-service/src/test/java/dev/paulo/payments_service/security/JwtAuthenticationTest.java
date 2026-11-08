package dev.paulo.payments_service.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import dev.paulo.payments_service.TestcontainersConfiguration;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class JwtAuthenticationTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${jwt.public-key}")
    private RSAPublicKey ordersPublicKey;

    @Value("classpath:test-keys/private.pem")
    private RSAPrivateKey ordersPrivateKey;

    @Test
    void health_withoutToken_returns200() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void protectedPath_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(PROTECTED_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.title").value("Authentication required"));
    }

    @Test
    void protectedPath_withTokenSignedByOrdersService_passesAuthentication() throws Exception {
        String token = sign(encoder(ordersPublicKey, ordersPrivateKey));

        mockMvc.perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void protectedPath_withTokenSignedByAnotherKey_returns401() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        String token = sign(encoder((RSAPublicKey) keys.getPublic(), (RSAPrivateKey) keys.getPrivate()));

        mockMvc.perform(get(PROTECTED_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication required"));
    }

    private JwtEncoder encoder(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
        RSAKey key = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    private String sign(JwtEncoder encoder) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("orders-service")
                .subject(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(15)))
                .build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
