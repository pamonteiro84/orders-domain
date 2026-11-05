package dev.paulo.orders_service.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jwt.SignedJWT;
import dev.paulo.orders_service.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void register_returns201WithoutPasswordHash() throws Exception {
        String email = randomEmail();

        register(email)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void register_withInvalidEmail_returns400() throws Exception {
        register("nao-e-email")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void register_withExistingEmail_returns409() throws Exception {
        String email = randomEmail();

        register(email).andExpect(status().isCreated());

        register(email)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Email already registered"));
    }

    @Test
    void register_withExistingEmailInUpperCase_returns409() throws Exception {
        String email = randomEmail();

        register(email).andExpect(status().isCreated());

        register(email.toUpperCase()).andExpect(status().isConflict());
    }

    @Test
    void login_withValidCredentials_returns200WithTokenForThatUser() throws Exception {
        String email = randomEmail();
        String registered = register(email).andReturn().getResponse().getContentAsString();
        String userId = JsonPath.read(registered, "$.id");

        String body = login(email, "segredo123")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = JsonPath.read(body, "$.accessToken");
        assertThat(SignedJWT.parse(token).getJWTClaimsSet().getSubject()).isEqualTo(userId);
    }

    @Test
    void login_withEmailInUpperCase_returns200() throws Exception {
        String email = randomEmail();
        register(email).andExpect(status().isCreated());

        login(email.toUpperCase(), "segredo123").andExpect(status().isOk());
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        String email = randomEmail();
        register(email).andExpect(status().isCreated());

        login(email, "errada")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid email or password"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void login_withUnknownEmail_returns401() throws Exception {
        login(randomEmail(), "segredo123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid email or password"));
    }

    @Test
    void login_withBlankPassword_returns400() throws Exception {
        login(randomEmail(), " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    private String randomEmail() {
        return "ana-" + UUID.randomUUID() + "@x.pt";
    }

    private ResultActions register(String email) throws Exception {
        return mockMvc.perform(
                post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                    { "email": "%s", "password": "segredo123" }
                    """.formatted(email)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(
                post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                    { "email": "%s", "password": "%s" }
                    """.formatted(email, password)));
    }
}
