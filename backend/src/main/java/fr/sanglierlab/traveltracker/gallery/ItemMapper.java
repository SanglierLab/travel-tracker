package fr.sanglierlab.traveltracker.gallery;

import fr.sanglierlab.traveltracker.media.MediaUrls;
import org.springframework.stereotype.Component;

@Component
public class ItemMapper {

    private final MarkdownService markdown;

    public ItemMapper(MarkdownService markdown) {
        this.markdown = markdown;
    }

    public ItemDto toDto(GalleryItem item) {
        long galleryId = item.getGalleryId();
        return switch (item.getType()) {
            case TEXT -> new ItemDto(item.getId(), ItemType.TEXT, item.getSortOrder(),
                    item.getTextMarkdown(), markdown.toHtml(item.getTextMarkdown()),
                    null, null, null, null, null, null, null);
            case PHOTO -> new ItemDto(item.getId(), ItemType.PHOTO, item.getSortOrder(),
                    null, null,
                    MediaUrls.thumb(galleryId, item.getFileKey()),
                    MediaUrls.display(galleryId, item.getFileKey()),
                    MediaUrls.original(galleryId, item.getFileKey(), item.getExtension()),
                    item.getOriginalFilename(), item.getWidth(), item.getHeight(), item.getSizeBytes());
            case VIDEO -> new ItemDto(item.getId(), ItemType.VIDEO, item.getSortOrder(),
                    null, null,
                    item.getWidth() != null ? MediaUrls.thumb(galleryId, item.getFileKey()) : null,
                    null,
                    MediaUrls.original(galleryId, item.getFileKey(), item.getExtension()),
                    item.getOriginalFilename(), item.getWidth(), item.getHeight(), item.getSizeBytes());
        };
    }
}
