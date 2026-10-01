package fr.sanglierlab.traveltracker.gallery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "gallery")
public class Gallery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 200)
    private String placeName;

    @Column(nullable = false)
    private LocalDate galleryDate;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    /** UTC */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** UTC */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Gallery() {
    }

    public Gallery(String title, String placeName, LocalDate galleryDate, BigDecimal latitude, BigDecimal longitude) {
        update(title, placeName, galleryDate, latitude, longitude);
    }

    public void update(String title, String placeName, LocalDate galleryDate, BigDecimal latitude, BigDecimal longitude) {
        this.title = title.strip();
        this.placeName = placeName.strip();
        this.galleryDate = galleryDate;
        this.latitude = latitude.setScale(6, RoundingMode.HALF_UP);
        this.longitude = longitude.setScale(6, RoundingMode.HALF_UP);
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getPlaceName() {
        return placeName;
    }

    public LocalDate getGalleryDate() {
        return galleryDate;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }
}
