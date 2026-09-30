package fr.sanglierlab.travel.trace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.sanglierlab.travel.common.BadRequestException;
import fr.sanglierlab.travel.trace.dto.IngestPointForm;
import fr.sanglierlab.travel.trace.dto.IngestResultDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Réception des positions transmises par le traceur mobile.
 *
 * Protégé par le jeton porteur défini dans la configuration serveur
 * ({@code app.ingest-token}), vérifié par une chaîne de sécurité dédiée et
 * sans session.
 *
 * Le corps accepte indifféremment un objet ou un tableau : le traceur envoie
 * une position isolée en fonctionnement normal, et un lot complet après une
 * coupure de réseau. Lui imposer deux formats distincts compliquerait
 * l'embarqué sans profit.
 */
@RestController
@RequestMapping("/api/ingest")
public class IngestController {

    private final TraceService service;
    private final ObjectMapper objectMapper;

    public IngestController(TraceService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/points")
    @ResponseStatus(HttpStatus.CREATED)
    public IngestResultDto ingest(@RequestBody JsonNode body) {
        List<IngestPointForm> forms = parse(body);
        return service.ingest(forms);
    }

    private List<IngestPointForm> parse(JsonNode body) {
        try {
            if (body.isArray()) {
                return objectMapper.convertValue(body,
                        objectMapper.getTypeFactory()
                                .constructCollectionType(List.class, IngestPointForm.class));
            }
            return List.of(objectMapper.convertValue(body, IngestPointForm.class));

        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Position illisible : " + e.getMessage());
        }
    }
}
