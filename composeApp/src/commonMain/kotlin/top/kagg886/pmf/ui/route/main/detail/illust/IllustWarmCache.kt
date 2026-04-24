package top.kagg886.pmf.ui.route.main.detail.illust

import top.kagg886.pixko.module.illust.Illust

object IllustWarmCache {
    private const val MaxEntries = 256
    private val cache = LinkedHashMap<Long, Illust>()

    fun get(id: Long): Illust? {
        val value = cache.remove(id) ?: return null
        cache[id] = value
        return value
    }

    fun put(illust: Illust) {
        cache[illust.id.toLong()] = illust
        trim()
    }

    fun putAll(illusts: Iterable<Illust>) {
        illusts.forEach(::put)
    }

    private fun trim() {
        while (cache.size > MaxEntries) {
            cache.entries.iterator().run {
                if (hasNext()) {
                    next()
                    remove()
                }
            }
        }
    }
}
