package fr.sanglierlab.traveltracker.gallery;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Block;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.ListBlock;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.AttributeProvider;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * Markdown volontairement limité : paragraphes, gras, italique, listes et liens.
 * <ul>
 *   <li>Titres, citations, blocs de code et séparateurs sont désactivés (le texte reste affiché tel quel).</li>
 *   <li>Le HTML brut est échappé, les URL dangereuses (javascript:...) sont neutralisées.</li>
 *   <li>Les images sont supprimées.</li>
 *   <li>Les liens s'ouvrent dans un nouvel onglet, sans transmettre le référent.</li>
 * </ul>
 */
@Service
public class MarkdownService {

    private final Parser parser = Parser.builder()
            .enabledBlockTypes(Set.<Class<? extends Block>>of(ListBlock.class))
            .build();

    private final HtmlRenderer renderer = HtmlRenderer.builder()
            .escapeHtml(true)
            .sanitizeUrls(true)
            .attributeProviderFactory(context -> new LinkAttributes())
            .build();

    public String toHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        Node document = parser.parse(markdown);
        document.accept(new ImageRemover());
        return renderer.render(document);
    }

    private static final class ImageRemover extends AbstractVisitor {
        @Override
        public void visit(Image image) {
            image.unlink();
        }
    }

    private static final class LinkAttributes implements AttributeProvider {
        @Override
        public void setAttributes(Node node, String tagName, Map<String, String> attributes) {
            if (node instanceof Link) {
                attributes.put("target", "_blank");
                attributes.put("rel", "noopener noreferrer nofollow");
            }
        }
    }
}
