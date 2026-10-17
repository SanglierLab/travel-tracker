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
 * Trajet suivi : un vol (identifié par son numéro de vol / indicatif), une traversée ou une session LiveTrack.
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

    /** Numéro de vol / indicatif (avion), MMSI (bateau), ou identifiant de session (LiveTrack). */
    @Column(nullable = false, length = 64)
    private String identifier;

    /** Secret d'accès du lien de partage (LiveTrack seulement) : jamais renvoyé par l'API ni journalisé. */
    @Column(length = 100)
    private String accessToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TripStatus status;

    /** Archivé : hors de la liste courante de l'admin ; les positions sont conservées. */
    @Column(nullable = false)
    private boolean archived;

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

    /** Trajet protégé par un jeton d'accès (session LiveTrack). */
    public TrackedTrip(TripType type, String label, LocalDateTime scheduledDeparture, String identifier, String accessToken) {
        this(type, label, scheduledDeparture, identifier);
        this.accessToken = accessToken;
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

    public void setLabel(String label) {
        this.label = label;
    }

    public void setStatus(TripStatus status) {
        this.status = status;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    /** Efface le jeton du lien de partage (à l'archivage d'une session LiveTrack). */
    public void clearAccessToken() {
        this.accessToken = null;
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

    public String getAccessToken() {
        return accessToken;
    }
}
