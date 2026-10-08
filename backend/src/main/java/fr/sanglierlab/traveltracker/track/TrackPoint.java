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

    /** Précision annoncée par le téléphone, en mètres ; null si inconnue. */
    @Column(precision = 7, scale = 1)
    private BigDecimal accuracyMeters;

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

    public TrackPoint(TrackSource source, BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters,
                      LocalDateTime recordedAt) {
        this.source = source;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyMeters = accuracyMeters;
        this.recordedAt = recordedAt;
    }

    /** Point rattaché à un trajet suivi (vol ADS-B, traversée AIS). */
    public TrackPoint(TrackSource source, Long tripId, BigDecimal latitude, BigDecimal longitude,
                      BigDecimal accuracyMeters, LocalDateTime recordedAt) {
        this(source, latitude, longitude, accuracyMeters, recordedAt);
        this.tripId = tripId;
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

    public BigDecimal getAccuracyMeters() {
        return accuracyMeters;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public Long getTripId() {
        return tripId;
    }
}
