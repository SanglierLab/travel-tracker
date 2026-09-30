package fr.sanglierlab.travel.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppPropertiesTest {

    private AppProperties withAdminUsers(List<String> adminUsers) {
        return new AppProperties(
                "Carnet de voyage",
                adminUsers,
                "jeton",
                5,
                "/tmp/tt",
                new AppProperties.Media("/media", 400, 1600, 0.85f,
                        List.of("image/jpeg"), List.of("video/mp4"), 1024),
                new AppProperties.Ffmpeg("ffmpeg", "ffprobe", 90, 1),
                new AppProperties.Trip("PT2M", 36));
    }

    @Test
    @DisplayName("découpe login:motdepasse")
    void parsesAccounts() {
        var accounts = withAdminUsers(List.of("voyageur:secret", "conjoint:autre")).accounts();

        assertThat(accounts).hasSize(2);
        assertThat(accounts.get(0).username()).isEqualTo("voyageur");
        assertThat(accounts.get(0).password()).isEqualTo("secret");
    }

    @Test
    @DisplayName("conserve les deux-points du mot de passe")
    void keepsColonsInPassword() {
        var accounts = withAdminUsers(List.of("voyageur:a:b:c")).accounts();

        assertThat(accounts.get(0).password()).isEqualTo("a:b:c");
    }

    @Test
    @DisplayName("refuse une entrée sans séparateur")
    void rejectsMalformedEntry() {
        assertThatThrownBy(() -> withAdminUsers(List.of("voyageur")).accounts())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("login:motdepasse");
    }
}
