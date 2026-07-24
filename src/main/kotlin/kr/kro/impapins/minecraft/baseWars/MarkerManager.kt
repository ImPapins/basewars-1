package kr.kro.impapins.minecraft.baseWars

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import kr.kro.impapins.minecraft.baseWars.TeamManager.getTeam
import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID

object MarkerManager : Listener {
    private val markers = mutableMapOf<UUID, ArmorStand>()

    fun init() {
        Bukkit.getOnlinePlayers().forEach(::createMarker)

        bukkitRunnable {
           for ((uuid, marker) in markers) {
               val target = Bukkit.getPlayer(uuid) ?: continue

               if (!target.isOnline) continue

               val success = marker.teleport(target.location)
               if (!success) {
                   plugin.logger.warning("Failed to teleport marker of ${target.name}")

                   if (target.y >= target.world.minHeight) createMarker(target)

                   continue
               }

               for (viewer in Bukkit.getOnlinePlayers()) {

                   if (viewer == target) {
                       viewer.hideEntity(plugin, marker)
                       continue
                   }

                   val visible =
                       viewer.getTeam()?.name == target.getTeam()?.name ||
                               (
                                       viewer.location.isWithinXZ(target.location, 100.0) &&
                                       !target.isSneaking
                               )

                   if (visible) {
                       viewer.showEntity(plugin, marker)
                   } else {
                       viewer.hideEntity(plugin, marker)
                   }
               }
           }
        }.runTaskTimer(plugin, 0L, 1L)
    }

    private fun createMarker(player: Player) {
        val marker = player.world.spawn(player.location, ArmorStand::class.java) {
            it.isInvisible = true
            it.isMarker = true
            it.isInvulnerable = true
            it.isSilent = true
            it.isPersistent = true
            it.removeWhenFarAway = false
            it.setGravity(false)
        }

        val team = player.getTeam()?.name ?: return
        Bukkit.getScoreboardManager().mainScoreboard
            .getTeam(team)
            ?.addEntity(marker)

        Bukkit.getOnlinePlayers().forEach { it.hideEntity(plugin, marker) }
        marker.getAttribute(Attribute.WAYPOINT_TRANSMIT_RANGE)
            ?.baseValue = 30000.0

        markers[player.uniqueId] = marker
    }

    fun removeMarker(player: Player) {
        markers.remove(player.uniqueId)?.remove()
    }

    fun removeAll() {
        Bukkit.getOnlinePlayers().forEach { removeMarker(it) }
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
         bukkitRunnable { createMarker(event.player) }.runTaskLater(plugin, 5L)
    }

    // @EventHandler
    // fun onRemove(event: EntityRemoveFromWorldEvent) {
    //     val marker = event.entity as? ArmorStand ?: return
    //     val owner = markers.entries.find {
    //         it.value.uniqueId == marker.uniqueId
    //     } ?: return
    //     Bukkit.getPlayer(owner.key)?.let {
    //         bukkitRunnable {
    //             createMarker(it)
    //         }.runTask(plugin)
    //     }
    // }

    // @EventHandler
    // fun onQuit(event: PlayerQuitEvent) {
    //     removeMarker(event.player)
    // }
}