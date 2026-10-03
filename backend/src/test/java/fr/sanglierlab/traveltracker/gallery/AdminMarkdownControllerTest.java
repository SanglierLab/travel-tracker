package fr.sanglierlab.traveltracker.gallery;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminMarkdownControllerTest {

    private final AdminMarkdownController controller = new AdminMarkdownController(new MarkdownService());

    @Test
    void l_apercuUtiliseLeMemeRenduQueLaPartiePublique() {
        String html = controller.preview(new TextRequest("**gras** <script>x</script>")).html();

        assertThat(html).contains("<strong>gras</strong>").doesNotContain("<script");
    }
}
