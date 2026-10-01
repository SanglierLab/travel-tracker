package fr.sanglierlab.traveltracker.media;

import fr.sanglierlab.traveltracker.gallery.ItemType;

/** Formats de médias acceptés (détectés sur le contenu réel du fichier, jamais sur son nom ou son type MIME déclaré). */
public enum MediaFormat {
    JPEG("jpg", ItemType.PHOTO),
    PNG("png", ItemType.PHOTO),
    WEBP("webp", ItemType.PHOTO),
    MP4("mp4", ItemType.VIDEO);

    private final String extension;
    private final ItemType type;

    MediaFormat(String extension, ItemType type) {
        this.extension = extension;
        this.type = type;
    }

    public String extension() {
        return extension;
    }

    public ItemType type() {
        return type;
    }
}
