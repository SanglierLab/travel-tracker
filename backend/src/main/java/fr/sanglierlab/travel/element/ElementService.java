package fr.sanglierlab.travel.element;

import fr.sanglierlab.travel.common.BadRequestException;
import fr.sanglierlab.travel.common.NotFoundException;
import fr.sanglierlab.travel.element.dto.ElementDto;
import fr.sanglierlab.travel.element.dto.ElementUpdateForm;
import fr.sanglierlab.travel.element.dto.ReorderForm;
import fr.sanglierlab.travel.element.dto.TextBlockForm;
import fr.sanglierlab.travel.gallery.Gallery;
import fr.sanglierlab.travel.gallery.GalleryService;
import fr.sanglierlab.travel.media.MediaService;
import fr.sanglierlab.travel.media.StoredFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Contenu d'une galerie : ajout de médias, insertion de récits, légendes,
 * réorganisation et suppression.
 */
@Service
public class ElementService {

    private static final Logger log = LoggerFactory.getLogger(ElementService.class);

    private final ElementRepository elements;
    private final GalleryService galleries;
    private final MediaService mediaService;

    public ElementService(ElementRepository elements,
                          GalleryService galleries,
                          MediaService mediaService) {
        this.elements = elements;
        this.galleries = galleries;
        this.mediaService = mediaService;
    }

    // ----------------------------------------------------------- ajout média

    /**
     * Ajoute plusieurs fichiers à la fin d'une galerie.
     *
     * Chaque fichier est traité indépendamment : un envoi de vingt photos dont
     * une corrompue en enregistre dix-neuf. Sur un réseau d'hôtel, tout rejeter
     * pour un seul fichier fautif serait décourageant.
     *
     * Le traitement se fait hors transaction — écriture disque et appels ffmpeg
     * sont longs, inutile de retenir une connexion à la base pendant ce temps.
     */
    public List<ElementDto> addMedia(Long galleryId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BadRequestException("Aucun fichier reçu");
        }
        galleries.require(galleryId);

        List<ElementDto> created = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                StoredFiles stored = mediaService.process(file);
                created.add(persistMedia(galleryId, file, stored));
            } catch (RuntimeException e) {
                log.warn("Fichier « {} » ignoré : {}", file.getOriginalFilename(), e.getMessage());
            }
        }

        if (created.isEmpty()) {
            throw new BadRequestException("Aucun fichier n'a pu être traité");
        }
        return created;
    }

    /** Écriture en base, transaction courte, une fois les fichiers prêts. */
    @Transactional
    protected ElementDto persistMedia(Long galleryId, MultipartFile file, StoredFiles stored) {
        Gallery gallery = galleries.require(galleryId);
        ElementType type = mediaService.detectKind(stored.mimeType) == MediaService.Kind.VIDEO
                ? ElementType.VIDEO
                : ElementType.PHOTO;

        Element element = Element.media(
                gallery,
                elements.findMaxPosition(galleryId) + 1,
                type,
                file.getOriginalFilename(),
                stored);

        return ElementDto.from(elements.save(element));
    }

    // ------------------------------------------------------------ bloc texte

    /**
     * Insère un bloc de récit, après un élément donné ou à la fin.
     *
     * L'insertion au milieu décale les suivants d'un cran, ce qui préserve un
     * ordre compact et sans trou.
     */
    @Transactional
    public ElementDto addTextBlock(Long galleryId, TextBlockForm form) {
        Gallery gallery = galleries.require(galleryId);

        int position;
        if (form.afterElementId() == null) {
            position = elements.findMaxPosition(galleryId) + 1;
        } else {
            Element anchor = requireInGallery(form.afterElementId(), galleryId);
            position = anchor.getPosition() + 1;
            shiftFrom(galleryId, position);
        }

        Element block = Element.text(gallery, position, form.markdown().trim());
        log.info("Bloc de texte ajouté en position {} de la galerie {}", position, galleryId);
        return ElementDto.from(elements.save(block));
    }

    // ------------------------------------------------------------ mise à jour

    @Transactional
    public ElementDto update(Long elementId, ElementUpdateForm form) {
        Element element = elements.findById(elementId)
                .orElseThrow(() -> NotFoundException.of("Élément", elementId));

        if (element.getType() == ElementType.TEXT) {
            if (form.markdown() == null || form.markdown().isBlank()) {
                throw new BadRequestException("Le texte ne peut pas être vide");
            }
            element.setMarkdown(form.markdown().trim());
        } else {
            element.setCaption(form.caption() == null ? null : form.caption().trim());
        }
        return ElementDto.from(element);
    }

    /**
     * Réorganise le flux à partir de la liste complète des identifiants.
     *
     * La liste doit décrire exactement le contenu de la galerie : c'est une
     * garantie contre les ordres partiels envoyés depuis un téléphone dont la
     * vue était périmée.
     */
    @Transactional
    public List<ElementDto> reorder(Long galleryId, ReorderForm form) {
        galleries.require(galleryId);
        List<Element> current = elements.findByGalleryIdOrderByPositionAsc(galleryId);

        Set<Long> expected = new HashSet<>(current.stream().map(Element::getId).toList());
        Set<Long> received = new HashSet<>(form.elementIds());

        if (!expected.equals(received) || form.elementIds().size() != current.size()) {
            throw new BadRequestException(
                    "La liste fournie ne correspond pas au contenu de la galerie. "
                            + "Rechargez la page avant de réorganiser.");
        }

        for (int index = 0; index < form.elementIds().size(); index++) {
            Long id = form.elementIds().get(index);
            for (Element element : current) {
                if (element.getId().equals(id)) {
                    element.setPosition(index);
                    break;
                }
            }
        }
        return current.stream()
                .sorted((a, b) -> Integer.compare(a.getPosition(), b.getPosition()))
                .map(ElementDto::from)
                .toList();
    }

    // ----------------------------------------------------------- suppression

    /**
     * Supprime un élément, puis ses fichiers.
     *
     * L'ordre importe : si la transaction échoue après l'effacement disque,
     * la base pointerait vers des fichiers absents. L'inverse — un fichier
     * orphelin — se corrige, une référence morte non.
     */
    @Transactional
    public void delete(Long elementId) {
        Element element = elements.findById(elementId)
                .orElseThrow(() -> NotFoundException.of("Élément", elementId));

        String storagePath = element.getStoragePath();
        String thumbPath = element.getThumbPath();
        String mediumPath = element.getMediumPath();
        Long galleryId = element.getGallery().getId();

        elements.delete(element);
        elements.flush();

        if (element.getType().isMedia()) {
            mediaService.deleteFiles(storagePath, thumbPath, mediumPath);
        }
        compact(galleryId);
        log.info("Élément {} supprimé de la galerie {}", elementId, galleryId);
    }

    @Transactional(readOnly = true)
    public List<ElementDto> listByGallery(Long galleryId) {
        return elements.findByGalleryIdOrderByPositionAsc(galleryId)
                .stream()
                .map(ElementDto::from)
                .toList();
    }

    // --------------------------------------------------------------- interne

    private Element requireInGallery(Long elementId, Long galleryId) {
        Element element = elements.findByIdWithGallery(elementId)
                .orElseThrow(() -> NotFoundException.of("Élément", elementId));
        if (!element.getGallery().getId().equals(galleryId)) {
            throw new BadRequestException("Cet élément appartient à une autre galerie");
        }
        return element;
    }

    /** Décale d'un cran tous les éléments situés à partir d'une position. */
    private void shiftFrom(Long galleryId, int fromPosition) {
        elements.findByGalleryIdOrderByPositionAsc(galleryId).stream()
                .filter(element -> element.getPosition() >= fromPosition)
                .forEach(element -> element.setPosition(element.getPosition() + 1));
        elements.flush();
    }

    /** Renumérote de 0 à n-1 après une suppression, pour éviter les trous. */
    private void compact(Long galleryId) {
        List<Element> remaining = elements.findByGalleryIdOrderByPositionAsc(galleryId);
        for (int index = 0; index < remaining.size(); index++) {
            remaining.get(index).setPosition(index);
        }
    }
}
