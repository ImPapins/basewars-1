package kr.kro.impapins.minecraft.baseWars

import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID

object CombatManager : Listener {
    val combatPlayers: MutableMap<UUID, MutableMap<UUID, Long>> = mutableMapOf()
    val bossBars: MutableMap<UUID, BossBar> = mutableMapOf()

    fun isCombatingWith(id1: UUID, id2: UUID) : Boolean {
        val time = combatPlayers[id1]?.get(id2) ?: return false

        if (System.currentTimeMillis() - time > 60_000L) {
            combatPlayers[id1]?.remove(id2)
            return false
        }

        return true
    }

    fun isCombating(id: UUID): Boolean {
        return combatPlayers[id]?.keys?.any {
            isCombatingWith(id, it)
        } ?: false
    }

    @EventHandler
    fun onCombat(event: EntityDamageByEntityEvent) {
        val player = event.entity
        val damager = when (val damager = event.damager) {
            is Player -> damager
            is Projectile -> damager.shooter as? Player
            else -> null
        } ?: return

        if (player !is Player) return

        val now = System.currentTimeMillis()

        if (!isCombating(player.uniqueId)) {
            player.sendMessage(
                Component.text("${damager.name}님이 당신을 공격했습니다. 1분간 전투 상황이 됩니다.")
            )
        }

        if (!isCombating(damager.uniqueId)) {
            damager.sendMessage(
                Component.text("${player.name}님을 공격했습니다. 1분간 전투 상황이 됩니다.")
            )
        }

        if (combatPlayers[player.uniqueId] == null) {
            combatPlayers[player.uniqueId] = mutableMapOf()
        }

        if (combatPlayers[damager.uniqueId] == null) {
            combatPlayers[damager.uniqueId] = mutableMapOf()
        }

        combatPlayers[player.uniqueId]?.set(damager.uniqueId, now)
        combatPlayers[damager.uniqueId]?.set(player.uniqueId, now)

        updateBossBar(player)
        updateBossBar(damager)
    }

    // @EventHandler
    // fun onPlayerQuit(event: PlayerQuitEvent) {
    //     val player = event.player
    //     val id = player.uniqueId
    //     if (isCombating(id)) {
    //         Bukkit.broadcast(Component.text("[알림] ${player.name}이(가) 추하게 싸우다가 게임을 나가서 죽었습니다"))
    //         player.health = 0.0
    //     }
    // }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.player
        val id = player.uniqueId

        cancelCombat(id)
    }

    fun updateBossBar(player: Player) {
        val bar = bossBars.getOrPut(player.uniqueId) {
            BossBar.bossBar(
                Component.text(),
                1.0f,
                BossBar.Color.RED,
                BossBar.Overlay.PROGRESS
            ).also {
                player.showBossBar(it)
            }
        }

        val task = object : BukkitRunnable() {
            override fun run() {
                val map = combatPlayers[player.uniqueId]

                if (map == null || map.count() == 0) {
                    bossBars.remove(player.uniqueId)
                    player.hideBossBar(bar)
                    cancel()
                    return
                }

                val max = map.values.max()

                val remaining = 60_000L - (System.currentTimeMillis() - max)
                val seconds = remaining / 1000

                if (remaining <= 0) {
                    bossBars.remove(player.uniqueId)
                    player.hideBossBar(bar)
                    cancel()
                    return
                }

                val names = map.keys.mapNotNull {
                    Bukkit.getPlayer(it)?.name
                }

                val text = if (names.size <= 3) {
                    names.joinToString(", ")
                } else {
                    names.take(3).joinToString(", ") + " 외 ${names.size - 3}명"
                } + "과 전투 중: ${seconds}초 남음"

                bar.name(
                    Component.text(
                        text,
                        NamedTextColor.GOLD
                    )
                )

                bar.progress(
                    (remaining / 60_000f)
                        .coerceIn(0f, 1f)
                )
            }
        }

        task.runTaskTimer(
            plugin,
            0L,
            20L
        )
    }

    fun cancelCombatWith(id1: UUID, id2: UUID) {
        combatPlayers[id1]?.remove(id2)
        combatPlayers[id2]?.remove(id1)
    }

    fun cancelCombat(id: UUID) {
        combatPlayers[id]?.keys?.map {
            if (isCombatingWith(id, it)) {
                cancelCombatWith(id, it)
            }
        }
    }
}