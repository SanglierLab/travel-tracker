package fr.sanglierlab.traveltracker.media;

import java.time.Instant;

public record PhotoExif(
        Integer iso,
        Double focalLength,          // mm
        Integer focalLength35mm,     // mm équivalent 24x36
        Double aperture,             // f/x
        String shutterSpeed,         // "1/250"
        Double exposureTimeSeconds,  // 0.004
        Instant takenAt
) {
    public static final PhotoExif EMPTY =
            new PhotoExif(null, null, null, null, null, null, null);
}