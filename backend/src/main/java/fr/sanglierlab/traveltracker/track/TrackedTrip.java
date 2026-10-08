package fr.sanglierlab.traveltracker.track;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Trajet suivi : un vol (identifié par son numéro de vol / indicatif) ou, plus tard, une traversée.
 * Toutes les heures sont en UTC.
 */
@Entity
@Table(name = "tracked_trip")
public class TrackedTrip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TripType type;

    @Column(nullable = false, length = 200)
    private String label;

    /** Date (et heure, facultative) théorique du départ. */
    @Column(nullable = false)
    private LocalDateTime scheduledDeparture;

    /** Numéro de vol / indicatif (avion), ou MMSI (bateau). */
    @Column(nullable = false, length = 20)
    private String identifier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TripStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected TrackedTrip() {
    }

    public TrackedTrip(TripType type, String label, LocalDateTime scheduledDeparture, String identifier) {
        this.type = type;
        this.label = label;
        this.scheduledDeparture = scheduledDeparture;
        this.identifier = identifier;
        this.status = TripStatus.PLANNED;
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

    public void setStatus(TripStatus status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public TripType getType() {
        return type;
    }

    public String getLabel() {
        return label;
    }

    public LocalDateTime getScheduledDeparture() {
        return scheduledDeparture;
    }

    public String getIdentifier() {
        return identifier;
    }

    public TripStatus getStatus() {
        return status;
    }
}
