package fr.sanglierlab.traveltracker.gallery;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/items")
public class AdminItemController {

    private final GalleryItemService items;
    private final ItemMapper mapper;

    public AdminItemController(GalleryItemService items, ItemMapper mapper) {
        this.items = items;
        this.mapper = mapper;
    }

    /** Modifie un bloc de texte. */
    @PutMapping("/{id}")
    public ItemDto updateText(@PathVariable long id, @Valid @RequestBody TextRequest request) {
        return mapper.toDto(items.updateText(id, request.markdown()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        items.delete(id);
    }

    /** Monte ou descend l'élément d'une position : {@code ?direction=UP} ou {@code ?direction=DOWN}. */
    @PostMapping("/{id}/move")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void move(@PathVariable long id, @RequestParam Direction direction) {
        items.move(id, direction);
    }
}
