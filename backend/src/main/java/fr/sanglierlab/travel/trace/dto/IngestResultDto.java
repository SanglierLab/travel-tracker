package fr.sanglierlab.travel.trace.dto;

/**
 * Bilan d'un envoi de positions.
 *
 * {@code duplicates} recense les points déjà connus, écartés sans erreur :
 * le traceur peut réémettre son tampon après une coupure sans provoquer de
 * doublons ni d'échec.
 */
public record IngestResultDto(int received, int stored, int duplicates, int rejected) {}
