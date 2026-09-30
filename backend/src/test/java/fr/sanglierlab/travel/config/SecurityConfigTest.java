package fr.sanglierlab.travel.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie les invariants de sécurité : lecture libre, administration fermée,
 * ingestion protégée par jeton, non-indexation systématique.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("la consultation publique reste ouverte")
    void publicReadIsOpen() throws Exception {
        mockMvc.perform(get("/api/galleries"))
                .andExpect(status().isNotFound());   // route pas encore implémentée, mais pas 401
    }

    @Test
    @DisplayName("l'administration exige une session")
    void adminRequiresSession() throws Exception {
        mockMvc.perform(get("/api/admin/galleries"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentification requise"));
    }

    @Test
    @DisplayName("l'ingestion sans jeton est refusée")
    void ingestRequiresToken() throws Exception {
        mockMvc.perform(post("/api/ingest/points"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("l'ingestion avec un mauvais jeton est refusée")
    void ingestRejectsWrongToken() throws Exception {
        mockMvc.perform(post("/api/ingest/points")
                        .header("Authorization", "Bearer mauvais-jeton"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("toute réponse porte l'en-tête de non-indexation")
    void noIndexHeaderIsAlwaysPresent() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(header().string("X-Robots-Tag",
                        "noindex, nofollow, noarchive, noimageindex, nosnippet"));
    }
}
