package fr.sanglierlab.traveltracker.media;

/** URLs publiques des fichiers (servis par nginx depuis le volume des médias). */
public final class MediaUrls {

    private MediaUrls() {
    }

    public static String thumb(long galleryId, String fileKey) {
        return "/media/" + galleryId + "/" + fileKey + "-thumb.jpg";
    }

    public static String display(long galleryId, String fileKey) {
        return "/media/" + galleryId + "/" + fileKey + "-display.jpg";
    }

    public static String original(long galleryId, String fileKey, String extension) {
        return "/media/" + galleryId + "/" + fileKey + "-original." + extension;
    }
}
