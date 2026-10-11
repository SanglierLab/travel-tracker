package fr.sanglierlab.traveltracker.stage;

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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Étape du voyage (lieu fixe ou trajet). Dates en jours ; heures techniques en UTC. */
@Entity
@Table(name = "stage")
public class Stage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StageKind kind;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal startLatitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal startLongitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal endLatitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal endLongitude;

    /** Tracé GPX allégé, au format de {@link PathCodec} ; null s'il n'y en a pas. */
    @Column
    private String pathData;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Stage() {
    }

    public Stage(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    /** Renseigne les champs saisis dans le formulaire (déjà validés par le service). */
    public void apply(String name, StageKind kind, LocalDate startDate, LocalDate endDate,
                      BigDecimal startLatitude, BigDecimal startLongitude, BigDecimal endLatitude, BigDecimal endLongitude) {
        this.name = name;
        this.kind = kind;
        this.startDate = startDate;
        this.endDate = endDate;
        setStart(startLatitude, startLongitude);
        setEnd(endLatitude, endLongitude);
    }

    public void setStart(BigDecimal latitude, BigDecimal longitude) {
        this.startLatitude = scale(latitude);
        this.startLongitude = scale(longitude);
    }

    public void setEnd(BigDecimal latitude, BigDecimal longitude) {
        this.endLatitude = scale(latitude);
        this.endLongitude = scale(longitude);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(6, RoundingMode.HALF_UP);
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

    public void setPathData(String pathData) {
        this.pathData = pathData;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public StageKind getKind() {
        return kind;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public BigDecimal getStartLatitude() {
        return startLatitude;
    }

    public BigDecimal getStartLongitude() {
        return startLongitude;
    }

    public BigDecimal getEndLatitude() {
        return endLatitude;
    }

    public BigDecimal getEndLongitude() {
        return endLongitude;
    }

    public String getPathData() {
        return pathData;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
