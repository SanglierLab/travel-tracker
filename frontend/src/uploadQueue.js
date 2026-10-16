import { computed, reactive } from 'vue'

// File d'envoi : les fichiers partent UN PAR UN (réseau mobile fragile : l'échec d'un fichier n'en bloque pas un autre,
// et on voit clairement lequel a réussi). Aucune dépendance au réseau ici : la fonction d'envoi est fournie.

let counter = 0

function describeError(error) {
  const status = error?.status
  if (status === 401) return 'Session expirée : reconnectez-vous, puis réessayez.'
  if (status === 413) return error.body?.message ?? 'Fichier trop volumineux.'
  if (error?.body?.message) return error.body.message
  if (status === 404) return "Cet envoi n'existe plus sur le serveur. Réessayez : il repartira du début."
  if (status >= 500 || status === 408 || status === 429) return `Le serveur ne répond pas correctement (erreur ${status}). Réessayez : l'envoi reprendra là où il s'est arrêté.`
  if (status) return `Le serveur a refusé le fichier (erreur ${status}).`
  return "Connexion trop instable. Ce qui a été envoyé est conservé : « Réessayer » reprend là où l'envoi s'est arrêté (c'est automatique au retour du réseau)."
}

/**
 * @param {(file: File, onProgress: (fraction: number) => void, signal: AbortSignal, onNote: (note: string) => void) => Promise<any>} send
 * @param {{ onUploaded?: (result: any) => void }} [options]
 */
export function createUploadQueue(send, { onUploaded } = {}) {
  // Chaque entrée : { id, file, name, size, status: 'waiting' | 'uploading' | 'done' | 'error', progress (0..1), note, error, network }
  // note : message passager pendant l'envoi (« connexion instable, nouvel essai… ») ; network : l'échec vient du réseau (reprise possible)
  const entries = reactive([])
  let running = false
  let current = null // { entry, controller } : l'envoi en cours

  const busy = computed(() => entries.some((e) => e.status === 'waiting' || e.status === 'uploading'))

  function add(files) {
    for (const file of files) {
      entries.push({ id: ++counter, file, name: file.name, size: file.size, status: 'waiting', progress: 0, note: '', error: '', network: false })
    }
    pump()
  }

  async function pump() {
    if (running) return
    running = true
    try {
      for (let entry = entries.find((e) => e.status === 'waiting'); entry; entry = entries.find((e) => e.status === 'waiting')) {
        await run(entry)
      }
    } finally {
      running = false
    }
  }

  async function run(entry) {
    entry.status = 'uploading'
    entry.error = ''
    entry.note = ''
    entry.network = false
    const controller = new AbortController()
    current = { entry, controller }
    try {
      const result = await send(
        entry.file,
        (fraction) => (entry.progress = fraction),
        controller.signal,
        (note) => (entry.note = note),
      )
      entry.status = 'done'
      entry.note = ''
      entry.progress = 1
      onUploaded?.(result)
    } catch (error) {
      if (controller.signal.aborted) {
        remove(entry) // annulé par l'utilisateur : ce n'est pas une erreur
      } else {
        entry.status = 'error'
        entry.note = ''
        entry.error = describeError(error)
        entry.network = !error?.status
      }
    } finally {
      current = null
    }
  }

  function remove(entry) {
    const index = entries.indexOf(entry)
    if (index >= 0) entries.splice(index, 1)
  }

  /** Annule un envoi en cours (interrompu net) ou retire un fichier en attente, en erreur ou terminé. */
  function cancel(entry) {
    if (current?.entry === entry) current.controller.abort()
    else remove(entry)
  }

  function retry(entry) {
    if (entry.status !== 'error') return
    entry.status = 'waiting'
    entry.error = ''
    pump()
  }

  function retryAll() {
    for (const entry of entries) {
      if (entry.status === 'error') {
        entry.status = 'waiting'
        entry.error = ''
      }
    }
    pump()
  }

  /** Relance les envois en échec à cause du réseau (au retour de la connexion). L'envoi reprend là où le serveur s'était arrêté. */
  function retryNetwork() {
    let any = false
    for (const entry of entries) {
      if (entry.status === 'error' && entry.network) {
        entry.status = 'waiting'
        entry.error = ''
        any = true
      }
    }
    if (any) pump()
  }

  function clearDone() {
    for (let i = entries.length - 1; i >= 0; i--) {
      if (entries[i].status === 'done') entries.splice(i, 1)
    }
  }

  /** Interrompt l'envoi en cours et vide la file (quand on quitte la page). */
  function dispose() {
    current?.controller.abort()
    entries.splice(0, entries.length)
  }

  return { entries, busy, add, cancel, retry, retryAll, retryNetwork, clearDone, dispose }
}
