package fr.sanglierlab.travel.trace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IngestControllerTest {

    private static final String TOKEN = "Bearer jeton-de-test";

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("une position isolée est acceptée")
    void acceptsSinglePoint() throws Exception {
        mockMvc.perform(post("/api/ingest/points")
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"measuredAt":"2026-10-10T08:00:00Z",
                                 "latitude":35.686,"longitude":139.753}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stored").value(1));
    }

    @Test
    @DisplayName("un lot de positions est accepté")
    void acceptsBatch() throws Exception {
        mockMvc.perform(post("/api/ingest/points")
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"measuredAt":"2026-10-11T08:00:00Z",
                                  "latitude":35.686,"longitude":139.753},
                                 {"measuredAt":"2026-10-11T08:01:00Z",
                                  "latitude":35.687,"longitude":139.754}]
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stored").value(2));
    }

    @Test
    @DisplayName("sans jeton, la position est refusée")
    void rejectsWithoutToken() throws Exception {
        mockMvc.perform(post("/api/ingest/points")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latitude":35.686,"longitude":139.753}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("une latitude hors limites est rejetée")
    void rejectsOutOfRangeLatitude() throws Exception {
        mockMvc.perform(post("/api/ingest/points")
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latitude":120.0,"longitude":139.753}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rejected").value(1));
    }
}
