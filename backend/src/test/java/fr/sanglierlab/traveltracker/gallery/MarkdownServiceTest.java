package fr.sanglierlab.traveltracker.gallery;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownServiceTest {

    private final MarkdownService markdown = new MarkdownService();

    @Test
    void rendLeGrasLItaliqueEtLesListes() {
        assertThat(markdown.toHtml("**gras** et *italique*")).contains("<strong>gras</strong>", "<em>italique</em>");
        assertThat(markdown.toHtml("- un\n- deux")).contains("<ul>", "<li>un</li>", "<li>deux</li>");
    }

    @Test
    void echappeLeHtmlBrut() {
        String html = markdown.toHtml("avant <script>alert(1)</script> après");
        assertThat(html).doesNotContain("<script").contains("&lt;script&gt;");
    }

    @Test
    void neutraliseLesUrlDangereuses() {
        assertThat(markdown.toHtml("[clic](javascript:alert(1))")).doesNotContain("javascript:");
    }

    @Test
    void lesLiensSOuvrentDansUnNouvelOnglet() {
        assertThat(markdown.toHtml("[site](https://example.org)"))
                .contains("href=\"https://example.org\"", "target=\"_blank\"", "noopener");
    }

    @Test
    void supprimeLesImages() {
        assertThat(markdown.toHtml("![photo](https://example.org/x.png)")).doesNotContain("<img");
    }

    @Test
    void neFabriquePasDeTitresNiDeCode() {
        assertThat(markdown.toHtml("# Titre")).doesNotContain("<h1");
        assertThat(markdown.toHtml("    code indenté")).doesNotContain("<pre");
    }

    @Test
    void gardeLesCaracteresNonLatins() {
        assertThat(markdown.toHtml("Visite du 皇居 (Palais impérial)")).contains("皇居");
    }

    @Test
    void texteVideDonneHtmlVide() {
        assertThat(markdown.toHtml("   ")).isEmpty();
        assertThat(markdown.toHtml(null)).isEmpty();
    }
}
