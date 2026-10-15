package fr.sanglierlab.traveltracker.feed;

import fr.sanglierlab.traveltracker.gallery.GallerySummary;
import fr.sanglierlab.traveltracker.stage.StageResponse;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ordre du fil : albums et étapes déjà commencées mélangés, du plus récent au plus ancien.
 * À date égale, les albums passent avant les étapes (une étape s'affiche « sous » les albums du jour) ;
 * entre albums, l'ordre reçu est conservé ; entre étapes, la plus avancée dans l'itinéraire passe en premier.
 */
public final class FeedOrder {

    public enum Type { GALLERY, STAGE }

    /** Une place dans le fil. */
    public record Slot(Type type, long id, LocalDate date, int tie) {
    }

    private FeedOrder() {
    }

    /**
     * @param galleries les albums, déjà dans l'ordre d'affichage (date décroissante)
     * @param stages    les étapes déjà commencées, dans l'ordre de l'itinéraire
     */
    public static List<Slot> order(List<GallerySummary> galleries, List<StageResponse> stages) {
        List<Slot> slots = new ArrayList<>(galleries.size() + stages.size());
        for (int i = 0; i < galleries.size(); i++) {
            GallerySummary g = galleries.get(i);
            slots.add(new Slot(Type.GALLERY, g.id(), g.galleryDate(), i));
        }
        for (int i = 0; i < stages.size(); i++) {
            StageResponse s = stages.get(i);
            slots.add(new Slot(Type.STAGE, s.id(), s.startDate(), -i));
        }
        slots.sort(Comparator.comparing(Slot::date).reversed()
                .thenComparing(Slot::type)
                .thenComparingInt(Slot::tie));
        return slots;
    }
}
