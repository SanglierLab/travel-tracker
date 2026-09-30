package fr.sanglierlab.travel.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("un visiteur non connecté obtient authenticated=false")
    void anonymousSession() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    @DisplayName("connexion valide puis session active")
    void loginThenSession() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"testeur","password":"motdepasse"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("testeur"));
    }

    @Test
    @DisplayName("mot de passe erroné : 401 avec message explicite")
    void loginRejectsWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"testeur","password":"faux"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Identifiant ou mot de passe incorrect"));
    }

    @Test
    @DisplayName("formulaire vide : 400 et détail des champs")
    void loginValidatesForm() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Formulaire invalide"));
    }
}
