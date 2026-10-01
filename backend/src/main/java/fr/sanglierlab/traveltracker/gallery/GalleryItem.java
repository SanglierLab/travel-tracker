package fr.sanglierlab.traveltracker.gallery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Élément d'une galerie : PHOTO, VIDEO ou TEXT.
 * <ul>
 *   <li>width / height : dimensions de l'image affichée (servent à réserver la place). Pour une vidéo,
 *       {@code null} signifie « pas de miniature » (l'extraction ffmpeg a échoué).</li>
 *   <li>Les fichiers sont déduits de galleryId + fileKey + extension (voir StorageService).</li>
 * </ul>
 */
@Entity
@Table(name = "gallery_item")
public class GalleryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long galleryId;

    @Column(nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ItemType type;

    private String textMarkdown;

    @Column(length = 36)
    private String fileKey;

    @Column(length = 255)
    private String originalFilename;

    @Column(length = 10)
    private String extension;

    private Long sizeBytes;

    private Integer width;

    private Integer height;

    /** UTC */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected GalleryItem() {
    }

    public static GalleryItem text(long galleryId, int sortOrder, String markdown) {
        GalleryItem item = new GalleryItem();
        item.galleryId = galleryId;
        item.sortOrder = sortOrder;
        item.type = ItemType.TEXT;
        item.textMarkdown = markdown;
        return item;
    }

    public static GalleryItem media(long galleryId, int sortOrder, ItemType type, String fileKey,
                                    String originalFilename, String extension, long sizeBytes,
                                    Integer width, Integer height) {
        GalleryItem item = new GalleryItem();
        item.galleryId = galleryId;
        item.sortOrder = sortOrder;
        item.type = type;
        item.fileKey = fileKey;
        item.originalFilename = originalFilename;
        item.extension = extension;
        item.sizeBytes = sizeBytes;
        item.width = width;
        item.height = height;
        return item;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public void setTextMarkdown(String textMarkdown) {
        this.textMarkdown = textMarkdown;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public Long getGalleryId() {
        return galleryId;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public ItemType getType() {
        return type;
    }

    public String getTextMarkdown() {
        return textMarkdown;
    }

    public String getFileKey() {
        return fileKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getExtension() {
        return extension;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }
}
