package fr.sanglierlab.traveltracker.track;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Un point de la route. Toutes les heures sont en UTC. */
@Entity
@Table(name = "track_point")
public class TrackPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TrackSource source;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    /** Heure du point (celle de l'appareil), précision à la milliseconde. */
    @Column(nullable = false)
    private LocalDateTime recordedAt;

    /** Trajet suivi (vol, traversée) auquel le point se rattache, s'il y en a un. */
    private Long tripId;

    /** Heure de réception par le serveur. */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected TrackPoint() {
    }

    public TrackPoint(TrackSource source, BigDecimal latitude, BigDecimal longitude, LocalDateTime recordedAt) {
        this.source = source;
        this.latitude = latitude;
        this.longitude = longitude;
        this.recordedAt = recordedAt;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public TrackSource getSource() {
        return source;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public Long getTripId() {
        return tripId;
    }
}
