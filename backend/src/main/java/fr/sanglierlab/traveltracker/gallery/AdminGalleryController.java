package fr.sanglierlab.traveltracker.gallery;

import fr.sanglierlab.traveltracker.media.MediaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/admin/galleries")
public class AdminGalleryController {

    private final GalleryService galleries;
    private final GalleryItemService items;
    private final MediaService media;
    private final ItemMapper mapper;

    public AdminGalleryController(GalleryService galleries, GalleryItemService items, MediaService media,
                                  ItemMapper mapper) {
        this.galleries = galleries;
        this.items = items;
        this.media = media;
        this.mapper = mapper;
    }

    @GetMapping
    public List<GallerySummary> list() {
        return galleries.summaries();
    }

    @GetMapping("/{id}")
    public GalleryDetail get(@PathVariable long id) {
        return galleries.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GalleryDetail create(@Valid @RequestBody GalleryRequest request) {
        return galleries.create(request);
    }

    @PutMapping("/{id}")
    public GalleryDetail update(@PathVariable long id, @Valid @RequestBody GalleryRequest request) {
        return galleries.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        galleries.delete(id);
    }

    /** Ajoute UNE photo ou vidéo (champ « file »). Le front envoie les fichiers un par un. */
    @PostMapping(value = "/{id}/items/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ItemDto uploadMedia(@PathVariable long id, @RequestParam("file") MultipartFile file) throws IOException {
        return media.upload(id, file);
    }

    @PostMapping("/{id}/items/text")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemDto addText(@PathVariable long id, @Valid @RequestBody TextRequest request) {
        return mapper.toDto(items.addText(id, request.markdown()));
    }
}
