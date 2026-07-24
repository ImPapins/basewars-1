package kr.kro.impapins.minecraft.baseWars

import com.google.gson.Gson
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Entity
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitRunnable
import java.util.Base64

lateinit var plugin: BaseWars

val keys: MutableMap<String, NamespacedKey> = mutableMapOf()

fun makeKey(key: String): NamespacedKey {
    return keys.getOrPut(key) { NamespacedKey(plugin, key) }
}

fun ItemStack.setTag(key: NamespacedKey) {
    persistentDataContainer
    val meta = itemMeta
    meta.persistentDataContainer.set(key, PersistentDataType.BOOLEAN, true)
    itemMeta = meta
}

fun ItemStack.removeTag(key: NamespacedKey) {
    val meta = itemMeta
    meta.persistentDataContainer.remove(key)
    itemMeta = meta
}

fun ItemStack.hasTag(key: NamespacedKey): Boolean =
    itemMeta?.persistentDataContainer?.has(key, PersistentDataType.BOOLEAN) == true

val gson = Gson()

fun Entity.setData(key: NamespacedKey, value: Any) {
    persistentDataContainer.set(
        key,
        PersistentDataType.STRING,
        gson.toJson(value)
    )
}

inline fun <reified T> Entity.getData(key: NamespacedKey): T? {
    val json = persistentDataContainer.get(
        key,
        PersistentDataType.STRING
    ) ?: return null

    return try {
        gson.fromJson(json, T::class.java)
    } catch (_: Exception) {
        null
    }
}

fun Entity.removeData(key: NamespacedKey) {
    persistentDataContainer.remove(key)
}

fun Entity.hasData(key: NamespacedKey): Boolean =
    persistentDataContainer.has(key, PersistentDataType.STRING)

fun Inventory.toStringList(): List<String?> =
    contents.map { item ->
        item?.let {
            Base64.getEncoder().encodeToString(it.serializeAsBytes())
        }
    }

fun List<String?>.toInventory(
    holder: InventoryHolder? = null
): Inventory {
    require(size in 9..54 && size % 9 == 0) {
        "인벤토리 크기는 9~54칸이며 9의 배수여야 합니다. (현재: $size)"
    }

    val inventory = Bukkit.createInventory(
        holder,
        size,
        Component.text("창고")
    )

    forEachIndexed { index, encoded ->
        if (encoded != null) {
            inventory.setItem(
                index,
                ItemStack.deserializeBytes(
                    Base64.getDecoder().decode(encoded)
                )
            )
        }
    }

    return inventory
}

fun Location.isWithinXZ(other: Location, distance: Double): Boolean {
    val dx = x - other.x
    val dz = z - other.z
    return dx * dx + dz *                                                                                                                                                                                                                                                     dz <= distance * distance
}

fun bukkitRunnable(task: BukkitRunnable.() -> Unit): BukkitRunnable {
    return object : BukkitRunnable() {
        override fun run() {
            task()
        }
    }
}

fun List<String?>.encode(): String = buildString {
    for (str in this@encode) {
        if (str == null) {
            append("-1:")
        } else {
            append(str.length)
            append(':')
            append(str)
        }
    }
}

fun String.decode(): MutableList<String?> {
    val result = mutableListOf<String?>()
    var i = 0

    while (i < length) {
        val colon = indexOf(':', i)
        require(colon != -1) { "잘못된 인코딩" }

        val len = substring(i, colon).toInt()

        if (len == -1) {
            result += null
            i = colon + 1
        } else {
            val start = colon + 1
            val end = start + len
            require(end <= length) { "잘못된 인코딩" }

            result += substring(start, end)
            i = end
        }
    }

    return result
}