package kr.kro.impapins.minecraft.baseWars

import kr.kro.impapins.minecraft.baseWars.TeamManager.getTeam
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.meta.CompassMeta
import java.util.UUID
import kotlin.math.pow

object BaseCompassManager {
    private val nextAlarm = mutableMapOf<UUID, Long>()

    fun init() {
        var tick = 0L

         bukkitRunnable {
            for (player in Bukkit.getOnlinePlayers()) {
                val uuid = player.uniqueId
                val item = player.inventory.itemInMainHand

                if (!item.hasTag(makeKey("baseCompass"))) {
                    nextAlarm.remove(uuid)
                    continue
                }

                val nearestBase = BaseManager.bases.values
                    .asSequence()
                    .filter { it.info.baseTeam != player.getTeam()?.name }
                    .filter { it.location.world == player.world }
                    .filter { !player.location.isWithinXZ(it.location, 100.0) }
                    .minByOrNull { it.location.distanceSquared(player.location) }

                if (nearestBase == null) {
                    player.sendActionBar(
                        Component.text(
                            "근처에 기지가 없습니다."
                        )
                    )

                    continue
                }

                val next = nextAlarm[uuid]
                val distance = nearestBase.location.distance(player.location)

                if (next == null || tick >= next) {
                    val t = (distance / 15000.0).coerceIn(0.0, 1.0)
                    val nxtTick = (1 + 249 * t.pow(1.5)).toLong()

                    nextAlarm[uuid] = tick + nxtTick

                    if (next != null) {
                        player.playSound(
                            player.location,
                            Sound.BLOCK_NOTE_BLOCK_BIT,
                            (nxtTick / 100.0f).coerceIn(0.5f, 2f),
                            2.0f
                        )
                    }
                }

                player.sendActionBar(
                    Component.text(
                        when (distance) {
                            in 0.0..200.0 -> "기지가 바로 근처에 있습니다..."
                            in 200.0..500.0 -> "기지가 아주 가까이에 있습니다..."
                            in 500.0..1000.0 -> "기지가 가까이에 있습니다..."
                            in 1000.0..1500.0 -> "기지의 흔적이 강하게 느껴집니다..."
                            in 1500.0..2000.0 -> "기지의 흔적이 감지됩니다..."
                            in 2000.0..2500.0 -> "신호가 감지됩니다..."
                            in 2500.0..5000.0 -> "희미한 신호가 감지됩니다..."
                            in 5000.0..10000.0 -> "아주 희미한 신호가 감지됩니다..."
                            else -> "매우 먼 곳에서 희미한 신호가 감지됩니다..."
                        }
                    )
                )

            }

            tick++
        }.runTaskTimer(plugin, 0L, 1L)
    }

    fun remove(player: Player) {
        nextAlarm.remove(player.uniqueId)
    }

    fun removeAll() {
        Bukkit.getOnlinePlayers().map(::remove)
    }

    // @EventHandler
    // fun onQuit(event: PlayerQuitEvent) {
    //     remove(event.player)
    // }
}