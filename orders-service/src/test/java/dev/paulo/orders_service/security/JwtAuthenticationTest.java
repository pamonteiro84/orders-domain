package dev.paulo.orders_service.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import dev.paulo.orders_service.TestcontainersConfiguration;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class JwtAuthenticationTest {

    private static final String ADAGIO = "00000000-0000-0000-0000-000000000001";
    private static final String UNKNOWN_ORDER = "/orders/00000000-0000-0000-0000-000000000999";
    private static final String ORDER_BODY = """
            { "items": [ { "productId": "%s", "quantity": 1 } ] }
            """.formatted(ADAGIO);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder encoder;

    @Test
    void tokenFromLogin_createsAndReadsOwnOrder() throws Exception {
        String token = tokenOfNewUser();

        String location = mockMvc.perform(withToken(post("/orders"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_BODY))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.LOCATION);

        mockMvc.perform(withToken(get(location), token)).andExpect(status().isOk());
        mockMvc.perform(withToken(get("/orders"), token))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void tokenFromLogin_ofAnotherUser_returns404() throws Exception {
        String location = mockMvc.perform(withToken(post("/orders"), tokenOfNewUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_BODY))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.LOCATION);

        mockMvc.perform(withToken(get(location), tokenOfNewUser())).andExpect(status().isNotFound());
    }

    @Test
    void malformedToken_returns401() throws Exception {
        expectUnauthorized(mockMvc.perform(withToken(get("/orders"), "nao-e-um-token")));
    }

    @Test
    void tokenSignedWithAnotherKey_returns401() throws Exception {
        String token = sign(anotherKeyEncoder(), Instant.now().plus(Duration.ofMinutes(15)));

        expectUnauthorized(mockMvc.perform(withToken(get("/orders"), token)));
    }

    @Test
    void expiredToken_returns401() throws Exception {
        String token = sign(encoder, Instant.now().minus(Duration.ofHours(1)));

        expectUnauthorized(mockMvc.perform(withToken(get("/orders"), token)));
    }

    @Test
    void createOrder_withoutToken_returns401() throws Exception {
        expectUnauthorized(mockMvc.perform(
                post("/orders").contentType(MediaType.APPLICATION_JSON).content(ORDER_BODY)));
    }

    @Test
    void getOrder_withoutToken_returns401() throws Exception {
        expectUnauthorized(mockMvc.perform(get(UNKNOWN_ORDER)));
    }

    @Test
    void cancelOrder_withoutToken_returns401() throws Exception {
        expectUnauthorized(mockMvc.perform(post(UNKNOWN_ORDER + "/cancel")));
    }

    @Test
    void listProducts_withoutToken_returns200() throws Exception {
        mockMvc.perform(get("/products")).andExpect(status().isOk());
    }

    private void expectUnauthorized(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.title").value("Authentication required"));
    }

    private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String tokenOfNewUser() throws Exception {
        String credentials = """
                { "email": "ana-%s@x.pt", "password": "segredo123" }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private String sign(JwtEncoder signer, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("orders-service")
                .subject(UUID.randomUUID().toString())
                .issuedAt(expiresAt.minus(Duration.ofMinutes(15)))
                .expiresAt(expiresAt)
                .build();
        return signer.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private JwtEncoder anotherKeyEncoder() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        RSAKey key = new RSAKey.Builder((RSAPublicKey) keys.getPublic())
                .privateKey((RSAPrivateKey) keys.getPrivate())
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }
}
