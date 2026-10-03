package fr.sanglierlab.traveltracker.gallery;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Aperçu d'un texte pendant sa rédaction : rendu par le même service que celui de la partie publique,
 * pour que l'aperçu soit identique à ce que verront les visiteurs.
 */
@RestController
@RequestMapping("/api/admin/markdown")
public class AdminMarkdownController {

    /** HTML déjà assaini (HTML brut échappé, URL dangereuses retirées, images supprimées). */
    public record PreviewResponse(String html) {
    }

    private final MarkdownService markdown;

    public AdminMarkdownController(MarkdownService markdown) {
        this.markdown = markdown;
    }

    @PostMapping("/preview")
    public PreviewResponse preview(@Valid @RequestBody TextRequest request) {
        return new PreviewResponse(markdown.toHtml(request.markdown()));
    }
}
