package fr.sanglierlab.traveltracker.feed;

import fr.sanglierlab.traveltracker.gallery.GalleryDetail;
import fr.sanglierlab.traveltracker.stage.StageResponse;

/** Une ligne du fil : un album (avec son contenu) ou une étape. L'autre champ est null. */
public record FeedEntry(FeedOrder.Type type, GalleryDetail gallery, StageResponse stage) {
}
