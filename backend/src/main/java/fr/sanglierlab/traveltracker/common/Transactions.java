package fr.sanglierlab.traveltracker.common;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class Transactions {

    private Transactions() {
    }

    /**
     * Exécute l'action une fois la transaction validée (commit). Sert à supprimer les fichiers
     * seulement si la suppression en base a réellement abouti.
     */
    public static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
