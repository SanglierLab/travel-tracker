package fr.sanglierlab.travel.trip;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Un vol ou une traversée dont la position est récupérée automatiquement.
 *
 * L'administrateur le déclare à l'avance : type, identifiant et heure de
 * départ prévue. À l'heure dite, le trajet passe en cours et une tâche
 * périodique interroge la source de données correspondante. Les positions
 * obtenues alimentent la même table que celles du téléphone, avec une source
 * distincte — la carte les différencie par la couleur et le style du tracé.
 *
 * La récupération effective des données ADS-B et AIS reste à écrire ; la
 * configuration, les états et le stockage sont en place.
 */
@Entity
@Table(name = "trip")
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private TripType type;

    /** Indicatif d'appel pour un vol, MMSI pour un navire. */
    @Column(nullable = false, length = 32)
    private String identifier;

    /** Libellé affiché, par exemple « Paris → Tokyo ». */
    @Column(length = 160)
    private String label;

    @Column(name = "scheduled_start", nullable = false)
    private Instant scheduledStart;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TripStatus status = TripStatus.PLANNED;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    /** Dernière interrogation de la source, pour diagnostic. */
    @Column(name = "last_polled_at")
    private Instant lastPolledAt;

    /** Couleur du tracé, si l'on souhaite distinguer deux vols consécutifs. */
    @Column(length = 7)
    private String color;

    @Column(name = "created_at", nullable = false, updatable = false,
            insertable = false, columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private Instant createdAt;

    protected Trip() {
        // JPA
    }

    public Trip(TripType type, String identifier, String label,
                Instant scheduledStart, String color) {
        this.type = type;
        this.identifier = identifier;
        this.label = label;
        this.scheduledStart = scheduledStart;
        this.color = color;
        this.status = TripStatus.PLANNED;
    }

    // ------------------------------------------------------- transitions

    /** Ouvre le suivi : la tâche périodique prendra le relais. */
    public void start() {
        this.status = TripStatus.ACTIVE;
        this.startedAt = Instant.now();
        this.finishedAt = null;
    }

    /** Clôt le suivi. La trace déjà enregistrée reste visible. */
    public void finish() {
        this.status = TripStatus.FINISHED;
        this.finishedAt = Instant.now();
    }

    /** Remet un trajet terminé à l'état planifié, en cas de report. */
    public void reset() {
        this.status = TripStatus.PLANNED;
        this.startedAt = null;
        this.finishedAt = null;
        this.lastPolledAt = null;
    }

    public void markPolled() {
        this.lastPolledAt = Instant.now();
    }

    public boolean isActive() {
        return status == TripStatus.ACTIVE;
    }

    // ------------------------------------------------------------- accès

    public Long getId() { return id; }
    public TripType getType() { return type; }
    public String getIdentifier() { return identifier; }
    public String getLabel() { return label; }
    public Instant getScheduledStart() { return scheduledStart; }
    public TripStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Instant getLastPolledAt() { return lastPolledAt; }
    public String getColor() { return color; }
    public Instant getCreatedAt() { return createdAt; }

    public void setType(TripType type) { this.type = type; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }
    public void setLabel(String label) { this.label = label; }
    public void setScheduledStart(Instant scheduledStart) { this.scheduledStart = scheduledStart; }
    public void setColor(String color) { this.color = color; }
    public void setStatus(TripStatus status) { this.status = status; }

    /** Libellé de repli lorsque l'administrateur n'en a pas saisi. */
    public String displayLabel() {
        return label != null && !label.isBlank() ? label : identifier;
    }
}
