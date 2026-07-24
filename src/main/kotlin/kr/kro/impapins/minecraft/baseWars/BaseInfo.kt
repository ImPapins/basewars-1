package kr.kro.impapins.minecraft.baseWars

import org.bukkit.Location
import java.util.UUID

data class BaseInfo(
    val baseTeam: String,
    var name: String,
    var level: Int,
    var inventoryEncoded: List<String?>,
    var hp: Double,
    var spawnLocation: Location,
    var spawnPlayers: MutableSet<UUID> = mutableSetOf()
)