package kr.kro.impapins.minecraft.baseWars

import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Location
import java.util.UUID

data class BaseTeam(
    val name: String,
    val color: NamedTextColor,
    val leader: UUID,
    val members: MutableSet<UUID> = mutableSetOf(),
    var teamSpawnLocation: Location? = null,
    var eliminated: Boolean = false
)