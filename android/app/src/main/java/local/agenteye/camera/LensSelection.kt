package local.agenteye.camera

/** Logical lens directions, not individual physical cameras in a multi-camera cluster. */
enum class Lens { BACK, FRONT, EXTERNAL }

/** Persistence is injected so selection and restart/fallback behavior can be tested on the JVM. */
class LensSelection(saved: String?, private val save: (String) -> Unit) {
    private var preferred = Lens.entries.find { it.name == saved } ?: Lens.BACK
    var available: List<Lens> = emptyList()
        private set
    var selected: Lens? = null
        private set

    fun discover(lenses: List<Lens>) {
        available = Lens.entries.filter { it in lenses }
        selected = preferred.takeIf { it in available }
            ?: Lens.BACK.takeIf { it in available } ?: available.firstOrNull()
        selected?.let {
            preferred = it
            save(it.name)
        }
    }

    fun cycle(): Lens? {
        if (available.size > 1) {
            selected = available[(available.indexOf(selected) + 1) % available.size]
            preferred = requireNotNull(selected)
            save(preferred.name)
        }
        return selected
    }
}
