package kr.kro.impapins.minecraft.baseWars

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitTask
import java.time.Duration
import java.util.UUID

object StayStillManager : Listener {
    private data class Waiting(
        val task: BukkitTask,
        val endTime: Long
    )

    private val waiting = mutableMapOf<UUID, Waiting>()

    fun waitForStill(player: Player, onSuccess: () -> Unit) {
        cancel(player, false)

        val endTime = System.currentTimeMillis() + 5000L

        val task = Bukkit.getScheduler().runTaskTimer(
            plugin,
            Runnable {
                val info = waiting[player.uniqueId] ?: return@Runnable

                val remainingMillis =
                    (info.endTime - System.currentTimeMillis()).coerceAtLeast(0)

                val remainingSeconds =
                    ((remainingMillis + 999) / 1000).toInt()

                player.addPotionEffect(
                    PotionEffect(
                        PotionEffectType.GLOWING,
                        5,
                        0,
                        false,
                        false,
                        false
                    )
                )

                if (remainingSeconds <= 0) {
                    info.task.cancel()
                    waiting.remove(player.uniqueId)

                    player.clearTitle()
                    onSuccess()
                    return@Runnable
                }

                player.showTitle(
                    Title.title(
                        Component.text("$remainingSeconds 초 남음")
                            .color(NamedTextColor.YELLOW),
                        Component.text("움직이면 텔레포트가 취소됩니다.")
                            .color(NamedTextColor.GRAY),
                        Title.Times.times(
                            Duration.ZERO,
                            Duration.ofMillis(1100),
                            Duration.ZERO
                        )
                    )
                )
            },
            0L,
            1L
        )

        waiting[player.uniqueId] = Waiting(task, endTime)
    }

    private fun cancel(player: Player, failure: Boolean) {
        val info = waiting.remove(player.uniqueId) ?: return

        info.task.cancel()
        player.clearTitle()

        if (failure) {
            player.sendMessage(
                Component.text("움직여서 텔레포트가 취소되었습니다.")
                    .color(NamedTextColor.RED)
            )

            player.playSound(
                player.location,
                Sound.BLOCK_NOTE_BLOCK_BASS,
                1f,
                0.5f
            )
        }
    }

    fun cancel(player: Player) {
        cancel(player, true)
    }

    @EventHandler(ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        val to = event.to
        val from = event.from

        val distanceSquared = from.distanceSquared(to)

        if (distanceSquared < 0.01 * 0.01) return

        if (waiting.containsKey(event.player.uniqueId)) {
            cancel(event.player)
        }
    }
}