package fr.sanglierlab.traveltracker.media;

import fr.sanglierlab.traveltracker.common.ApiException;
import fr.sanglierlab.traveltracker.config.AppProperties;
import fr.sanglierlab.traveltracker.gallery.GalleryRepository;
import fr.sanglierlab.traveltracker.gallery.ItemDto;
import fr.sanglierlab.traveltracker.gallery.ItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadServiceTest {

    @TempDir
    Path dir;

    private GalleryRepository galleries;
    private MediaService media;
    private UploadService service;

    /** Un JPEG « de façade » : seule l'en-tête compte pour la détection du type. */
    private static byte[] jpeg(int size) {
        byte[] data = new byte[size];
        data[0] = (byte) 0xFF;
        data[1] = (byte) 0xD8;
        data[2] = (byte) 0xFF;
        for (int i = 3; i < size; i++) {
            data[i] = (byte) (i % 251);
        }
        return data;
    }

    @BeforeEach
    void setUp() {
        galleries = mock(GalleryRepository.class);
        media = mock(MediaService.class);
        when(galleries.existsById(1L)).thenReturn(true);
        when(media.detectHeader(any())).thenAnswer(invocation -> {
            byte[] header = invocation.getArgument(0);
            return FileTypeDetector.detect(header).orElseThrow(() -> ApiException.unsupportedMedia("Format non accepté."));
        });
        AppProperties properties = new AppProperties(List.of(), "x".repeat(24), dir, DataSize.ofMegabytes(30), DataSize.ofMegabytes(500),
                10, 6, 100, 5, Duration.ofMinutes(15), "ffmpeg", 7,
                new AppProperties.LateAfter(Duration.ofMinutes(10), Duration.ofMinutes(5), Duration.ofMinutes(20), Duration.ofMinutes(5)));
        service = new UploadService(dir.resolve(".uploads"), galleries, media, properties);
    }

    private UploadStatus start(byte[] data, String fingerprint) throws IOException {
        return service.start(1, new UploadRequest("photo.jpg", data.length, fingerprint));
    }

    private UploadStatus send(UploadStatus session, byte[] data, int from, int to) throws IOException {
        byte[] chunk = Arrays.copyOfRange(data, from, to);
        return service.append(session.id(), from, new ByteArrayInputStream(chunk), chunk.length);
    }

    @Test
    void recoitUnFichierParMorceauxPuisLeValide() throws IOException {
        byte[] data = jpeg(1000);
        UploadStatus session = start(data, "photo.jpg|1000|1");
        assertThat(session.received()).isZero();

        assertThat(send(session, data, 0, 400).received()).isEqualTo(400);
        assertThat(send(session, data, 400, 1000).received()).isEqualTo(1000);

        ItemDto item = new ItemDto(7, ItemType.PHOTO, 1, null, null, null, null, null, "photo.jpg", null, null, 1000L, null);
        when(media.ingest(eq(1L), any(Path.class), eq("photo.jpg"))).thenAnswer(invocation -> {
            assertThat(Files.readAllBytes(invocation.getArgument(1))).isEqualTo(data); // le fichier est complet et intact
            Files.delete(invocation.getArgument(1)); // comme le fait MediaService (déplacement)
            return item;
        });

        assertThat(service.complete(session.id()).id()).isEqualTo(7);
    }

    @Test
    void validerDeuxFoisRenvoieLeMemeElementSansTraiterDeNouveau() throws IOException {
        byte[] data = jpeg(300);
        UploadStatus session = start(data, "f");
        send(session, data, 0, 300);
        ItemDto item = new ItemDto(7, ItemType.PHOTO, 1, null, null, null, null, null, "photo.jpg", null, null, 300L, null);
        when(media.ingest(anyLong(), any(Path.class), any())).thenAnswer(invocation -> {
            Files.delete(invocation.getArgument(1));
            return item;
        });
        when(media.existing(7)).thenReturn(item);

        ItemDto first = service.complete(session.id());
        ItemDto second = service.complete(session.id());

        assertThat(second.id()).isEqualTo(first.id());
        verify(media, times(1)).ingest(anyLong(), any(Path.class), any());
    }

    @Test
    void unMorceauAuMauvaisDecalageEstRefuseAvecLaPositionAttendue() throws IOException {
        byte[] data = jpeg(1000);
        UploadStatus session = start(data, "f");
        send(session, data, 0, 400);

        assertThatThrownBy(() -> send(session, data, 600, 1000))
                .isInstanceOfSatisfying(UploadOffsetException.class, e -> assertThat(e.received()).isEqualTo(400));
        // Un morceau déjà reçu (réponse perdue puis réessai) est refusé de la même façon : le client se resynchronise.
        assertThatThrownBy(() -> send(session, data, 0, 400))
                .isInstanceOfSatisfying(UploadOffsetException.class, e -> assertThat(e.received()).isEqualTo(400));
        assertThat(service.status(session.id()).received()).isEqualTo(400);
    }

    /** Un flux qui casse après quelques octets, comme une connexion mobile qui tombe. */
    private static InputStream breaking(byte[] chunk, int breakAfter) {
        return new InputStream() {
            private int position;

            @Override
            public int read() throws IOException {
                if (position >= breakAfter) {
                    throw new IOException("Connexion coupée");
                }
                return chunk[position++] & 0xFF;
            }
        };
    }

    @Test
    void unMorceauCoupeEnRouteNeLaisseRienDansLeFichier() throws IOException {
        byte[] data = jpeg(1000);
        UploadStatus session = start(data, "f");
        send(session, data, 0, 300);
        byte[] chunk = Arrays.copyOfRange(data, 300, 800);

        assertThatThrownBy(() -> service.append(session.id(), 300, breaking(chunk, 120), chunk.length)).isInstanceOf(IOException.class);

        assertThat(service.status(session.id()).received()).isEqualTo(300);
        // Le même morceau, renvoyé en entier, est accepté.
        assertThat(send(session, data, 300, 800).received()).isEqualTo(800);
    }

    @Test
    void unMorceauPlusCourtQueAnnonceEstRetire() throws IOException {
        byte[] data = jpeg(1000);
        UploadStatus session = start(data, "f");
        send(session, data, 0, 300);

        assertThatThrownBy(() -> service.append(session.id(), 300, new ByteArrayInputStream(Arrays.copyOfRange(data, 300, 400)), 500))
                .isInstanceOf(ApiException.class);
        assertThat(service.status(session.id()).received()).isEqualTo(300);
    }

    @Test
    void refuseDeDepasserLaTailleAnnoncee() throws IOException {
        byte[] data = jpeg(500);
        UploadStatus session = start(data, "f");
        send(session, data, 0, 300);

        assertThatThrownBy(() -> service.append(session.id(), 300, new ByteArrayInputStream(new byte[400]), -1))
                .isInstanceOf(ApiException.class);
        assertThat(service.status(session.id()).received()).isEqualTo(300);
    }

    @Test
    void validerUnEnvoiIncompletDemandeLaReprise() throws IOException {
        byte[] data = jpeg(1000);
        UploadStatus session = start(data, "f");
        send(session, data, 0, 600);

        assertThatThrownBy(() -> service.complete(session.id()))
                .isInstanceOfSatisfying(UploadOffsetException.class, e -> assertThat(e.received()).isEqualTo(600));
        verify(media, never()).ingest(anyLong(), any(Path.class), any());
    }

    @Test
    void leMemeFichierReprendLEnvoiInterrompu() throws IOException {
        byte[] data = jpeg(1000);
        UploadStatus first = start(data, "photo.jpg|1000|1700000000000");
        send(first, data, 0, 400);

        UploadStatus again = start(data, "photo.jpg|1000|1700000000000");

        assertThat(again.id()).isEqualTo(first.id());
        assertThat(again.received()).isEqualTo(400);
    }

    @Test
    void uneAutreEmpreinteOuUneAutreTailleCreeUnNouvelEnvoi() throws IOException {
        byte[] data = jpeg(1000);
        UploadStatus first = start(data, "photo.jpg|1000|1");

        assertThat(start(data, "photo.jpg|1000|2").id()).isNotEqualTo(first.id());
        assertThat(service.start(1, new UploadRequest("photo.jpg", 999, "photo.jpg|1000|1")).id()).isNotEqualTo(first.id());
        assertThat(service.start(1, new UploadRequest("photo.jpg", 1000, null)).id()).isNotEqualTo(first.id());
    }

    @Test
    void unFichierQuiNEstPasUneImageOuUneVideoEstRefuseDesLePremierMorceau() throws IOException {
        byte[] data = new byte[2000];
        Arrays.fill(data, (byte) 'a');
        UploadStatus session = start(data, "f");

        assertThatThrownBy(() -> send(session, data, 0, 1000)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.status(session.id())).isInstanceOf(ApiException.class); // l'envoi est effacé
    }

    @Test
    void uneLimiteDeTailleDepasseeEstRefuseeDesLePremierMorceau() throws IOException {
        byte[] data = jpeg(1000);
        UploadStatus session = start(data, "f");
        org.mockito.Mockito.doThrow(ApiException.payloadTooLarge("Fichier trop volumineux")).when(media).checkDeclaredSize(any(), eq(1000L));

        assertThatThrownBy(() -> send(session, data, 0, 500)).isInstanceOf(ApiException.class).hasMessageContaining("volumineux");
        assertThatThrownBy(() -> service.status(session.id())).isInstanceOf(ApiException.class);
    }

    @Test
    void refuseUnFichierPlusGrosQueLaPlusGrandeLimite() {
        assertThatThrownBy(() -> service.start(1, new UploadRequest("v.mp4", 501L * 1024 * 1024, "f")))
                .isInstanceOf(ApiException.class).hasMessageContaining("volumineux");
    }

    @Test
    void refuseUneGalerieInconnue() {
        assertThatThrownBy(() -> service.start(99, new UploadRequest("p.jpg", 10, "f"))).isInstanceOf(ApiException.class);
    }

    @Test
    void unIdentifiantInventeEstUnEnvoiInconnu() {
        assertThatThrownBy(() -> service.status("../../etc/passwd")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.status("6e0f3a52-3f5d-4a5e-9f5d-0123456789ab")).isInstanceOf(ApiException.class);
    }

    @Test
    void annulerEffaceLEnvoi() throws IOException {
        byte[] data = jpeg(500);
        UploadStatus session = start(data, "f");
        send(session, data, 0, 300);

        service.cancel(session.id());

        assertThatThrownBy(() -> service.status(session.id())).isInstanceOf(ApiException.class);
        try (var files = Files.list(dir.resolve(".uploads"))) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void unFichierRefuseALaValidationEstEfface() throws IOException {
        byte[] data = jpeg(300);
        UploadStatus session = start(data, "f");
        send(session, data, 0, 300);
        when(media.ingest(anyLong(), any(Path.class), any())).thenThrow(ApiException.unsupportedMedia("Format non accepté."));

        assertThatThrownBy(() -> service.complete(session.id())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.status(session.id())).isInstanceOf(ApiException.class);
    }

    @Test
    void unEchecDeTraitementAvantLeDeplacementGardeLEnvoiPourReessayer() throws IOException {
        byte[] data = jpeg(300);
        UploadStatus session = start(data, "f");
        send(session, data, 0, 300);
        when(media.ingest(anyLong(), any(Path.class), any())).thenThrow(new IOException("disque plein"));

        assertThatThrownBy(() -> service.complete(session.id())).isInstanceOf(IOException.class);
        assertThat(service.status(session.id()).received()).isEqualTo(300);
    }

    @Test
    void leNettoyageEffaceLesEnvoisAbandonnesDepuisPlusDe48Heures() throws IOException {
        byte[] data = jpeg(500);
        UploadStatus old = start(data, "ancien");
        send(old, data, 0, 300);
        UploadStatus recent = start(data, "recent");
        send(recent, data, 0, 300);
        Instant stale = Instant.now().minus(UploadService.EXPIRY).minusSeconds(3600);
        for (String extension : new String[]{"part", "meta"}) {
            Files.setLastModifiedTime(dir.resolve(".uploads").resolve(old.id() + "." + extension), FileTime.from(stale));
        }

        service.purgeExpired();

        assertThatThrownBy(() -> service.status(old.id())).isInstanceOf(ApiException.class);
        assertThat(service.status(recent.id()).received()).isEqualTo(300);
    }
}
