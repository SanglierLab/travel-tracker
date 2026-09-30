package fr.sanglierlab.travel.trace;

import fr.sanglierlab.travel.trip.Trip;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Une position horodatée.
 *
 * Deux horodatages sont conservés : {@code measuredAt}, l'instant de la mesure,
 * et {@code receivedAt}, celui de l'enregistrement. Ils diffèrent dès que le
 * téléphone accumule des points hors réseau puis les transmet en bloc —
 * situation ordinaire en voyage. Seul le premier fait foi pour l'ordre du
 * tracé.
 */
@Entity
@Table(name = "trace_point")
public class TracePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private TraceSource source;

    /** Renseigné pour les sources ADS-B et AIS, nul pour le téléphone. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    @Column(name = "received_at", nullable = false, updatable = false,
            insertable = false, columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private Instant receivedAt;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "altitude_m")
    private Double altitudeM;

    @Column(name = "speed_kmh")
    private Double speedKmh;

    @Column(name = "heading_deg")
    private Double headingDeg;

    @Column(name = "accuracy_m")
    private Double accuracyM;

    protected TracePoint() {
        // JPA
    }

    public TracePoint(TraceSource source, Trip trip, Instant measuredAt,
                      double latitude, double longitude) {
        this.source = source;
        this.trip = trip;
        this.measuredAt = measuredAt;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // ------------------------------------------------------------- accès

    public Long getId() { return id; }
    public TraceSource getSource() { return source; }
    public Trip getTrip() { return trip; }
    public Instant getMeasuredAt() { return measuredAt; }
    public Instant getReceivedAt() { return receivedAt; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public Double getAltitudeM() { return altitudeM; }
    public Double getSpeedKmh() { return speedKmh; }
    public Double getHeadingDeg() { return headingDeg; }
    public Double getAccuracyM() { return accuracyM; }

    public void setAltitudeM(Double altitudeM) { this.altitudeM = altitudeM; }
    public void setSpeedKmh(Double speedKmh) { this.speedKmh = speedKmh; }
    public void setHeadingDeg(Double headingDeg) { this.headingDeg = headingDeg; }
    public void setAccuracyM(Double accuracyM) { this.accuracyM = accuracyM; }

    public void moveTo(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void setMeasuredAt(Instant measuredAt) {
        this.measuredAt = measuredAt;
    }
}
