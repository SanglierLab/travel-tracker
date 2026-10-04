package fr.sanglierlab.traveltracker.auth;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ApiTokenFilterTest {

    private static final String TOKEN = "un-token-assez-long-et-aleatoire-123";

    private final ApiTokenFilter filter = new ApiTokenFilter(TOKEN);

    @AfterEach
    void clean() {
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletRequest post(String path, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        if (token != null) {
            request.addHeader(ApiTokenFilter.HEADER, token);
        }
        return request;
    }

    @Test
    void unTokenValideAuthentifieLaRequete() throws Exception {
        AtomicReference<Authentication> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(SecurityContextHolder.getContext().getAuthentication());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(post("/api/track/points", TOKEN), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(seen.get()).isNotNull();
        assertThat(seen.get().getAuthorities()).extracting(Object::toString).containsExactly("ROLE_TRACKER");
    }

    @Test
    void unTokenFauxOuAbsentEstRefuse() throws Exception {
        for (String wrong : new String[]{"mauvais-token", "", TOKEN + "x", null}) {
            AtomicBoolean called = new AtomicBoolean();
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(post("/api/track/points", wrong), response, (req, res) -> called.set(true));

            assertThat(response.getStatus()).as("token %s", wrong).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("Token d'API");
            assertThat(called).as("la requête ne doit pas passer (%s)", wrong).isFalse();
        }
    }

    @Test
    void lesAutresRoutesNeSontPasConcernees() throws Exception {
        AtomicReference<Authentication> seen = new AtomicReference<>();
        AtomicBoolean called = new AtomicBoolean();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(post("/api/public/map", null), response, (req, res) -> {
            called.set(true);
            seen.set(SecurityContextHolder.getContext().getAuthentication());
        });

        assertThat(called).isTrue();
        assertThat(seen.get()).isNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
