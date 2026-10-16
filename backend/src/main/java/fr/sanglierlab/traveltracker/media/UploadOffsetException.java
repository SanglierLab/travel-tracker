package fr.sanglierlab.traveltracker.media;

/** Le morceau reçu ne commence pas là où le serveur en est : le client doit reprendre à {@code received}. */
public class UploadOffsetException extends RuntimeException {

    private final long received;

    public UploadOffsetException(long received) {
        super("Reprise à l'octet " + received);
        this.received = received;
    }

    public long received() {
        return received;
    }
}
