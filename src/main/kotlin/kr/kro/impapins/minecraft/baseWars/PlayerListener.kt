package kr.kro.impapins.minecraft.baseWars

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent
import kr.kro.impapins.minecraft.baseWars.BaseManager.baseHp
import kr.kro.impapins.minecraft.baseWars.BaseManager.getSpawnBase
import kr.kro.impapins.minecraft.baseWars.BaseManager.newBase
import kr.kro.impapins.minecraft.baseWars.CombatManager.isCombating
import kr.kro.impapins.minecraft.baseWars.CustomDeathReason.*
import kr.kro.impapins.minecraft.baseWars.TeamManager.getTeam
import kr.kro.impapins.minecraft.baseWars.TeamManager.isLeader
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object PlayerListener : Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = 40.0
        player.getAttribute(Attribute.WAYPOINT_TRANSMIT_RANGE)?.baseValue = 0.0

        val team = player.getTeam()

        if (team == null) {
            event.joinMessage(null)
            player.kick(Component.text("당신은 팀이 없는 찐따입니다 ㅋㅋ ㅅㄱ"))
            return
        }

        if (team.eliminated) {
            event.joinMessage(null)
            player.kick(Component.text("당신의 팀은 망했습니다 xx"))
            return
        }

        if (!player.hasPlayedBefore()) {
            player.health = 40.0

            if (player.isLeader()) {
                val block = team.teamSpawnLocation!!.clone().add(0.0, 1.0, 0.0).block
                val location = block.location

                block.type = Material.AIR

                val base = newBase(
                    location,
                    BaseInfo(
                        baseTeam         = team.name,
                        level            = 1,
                        inventoryEncoded = emptyList(),
                        name             = "${team.name} 기지",
                        hp               = baseHp[1],
                        spawnLocation    = location.clone().add(0.5, -1.0, 0.5)
                    )
                )

                val spawnBases = team.members.associateWith(::getSpawnBase)
                spawnBases.forEach { (id, spawnBase) ->
                    if (spawnBase == null) {
                        base.info.spawnPlayers += id
                    }
                }

                BaseManager.save()
            }

            player.teleport(team.teamSpawnLocation!!)
        }

        Bukkit.getScoreboardManager()
            .mainScoreboard
            .getTeam(team.name)
            ?.addEntry(player.name)
    }

    @EventHandler
    fun onKick(event: PlayerKickEvent) {
        event.leaveMessage(Component.empty())
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        val player = event.entity

        event.droppedExp = player.calculateTotalExperiencePoints()

        val reason = player.getData<CustomDeathReason>(makeKey("lastDeathReason"))

        event.deathMessage(
            when (reason) {
                SELF_DEATH ->
                    player.teamDisplayName()
                        .append(
                            Component.text(
                                "이(가) 자살했습니다\n(X를 눌러 조의를 표하시오)"
                            ).color(NamedTextColor.WHITE)
                        )

                COMBAT_LEAVE ->
                    player.teamDisplayName()
                        .append(
                            Component.text(
                                "이(가) 추하게 싸우다가 게임을 나가서 죽었습니다"
                            ).color(NamedTextColor.WHITE)
                        )

                else -> event.deathMessage()
            }
        )

        player.removeData(makeKey("lastDeathReason"))

        if (!isCombating(player.uniqueId)) return

        val head = ItemStack(Material.PLAYER_HEAD)
        val meta = head.itemMeta as SkullMeta

        val now = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일, HH:mm:ss")
        val formatted = now.format(formatter)

        meta.owningPlayer = player

        meta.displayName(
            Component.text(
                "${player.name}의 머리",
                NamedTextColor.RED
            )
                .decoration(TextDecoration.ITALIC, false)
        )

        meta.lore(
            listOf(
                event.deathMessage()
                    ?.color(NamedTextColor.DARK_PURPLE)
                    ?.decorate(TextDecoration.BOLD)
                    ?.decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text(formatted)
                    .color(NamedTextColor.GRAY)
                    .decorate(TextDecoration.ITALIC)
                    .decoration(TextDecoration.ITALIC, false)
            )
        )

        head.itemMeta = meta

        head.setTag(makeKey("playerHead"))

        event.drops.add(head)
    }

    @EventHandler
    fun onHeadUse(event: PlayerInteractEvent) {
        val player = event.player
        if (event.action.isLeftClick) return

        val item = event.item ?: return
        if (!item.hasTag(makeKey("playerHead"))) return

        event.isCancelled = true

        if (item.hasTag(makeKey("goldenHead"))) {
            player.addPotionEffect(
                PotionEffect(
                    PotionEffectType.SPEED,
                    300,
                    4
                )
            )

            player.addPotionEffect(
                PotionEffect(
                    PotionEffectType.REGENERATION,
                    120,
                    2
                )
            )

            player.addPotionEffect(
                PotionEffect(
                    PotionEffectType.ABSORPTION,
                    400,
                    2
                )
            )
        } else {
            player.addPotionEffect(
                PotionEffect(
                    PotionEffectType.SPEED,
                    200,
                    3
                )
            )

            player.addPotionEffect(
                PotionEffect(
                    PotionEffectType.REGENERATION,
                    72,
                    2
                )
            )

            player.addPotionEffect(
                PotionEffect(
                    PotionEffectType.ABSORPTION,
                    400,
                    1
                )
            )
        }


        item.amount--
    }

    @EventHandler
    fun onSetSpawn(event: PlayerSetSpawnEvent) {
        if (
            event.cause == PlayerSetSpawnEvent.Cause.BED ||
            event.cause == PlayerSetSpawnEvent.Cause.RESPAWN_ANCHOR
        ) {
            event.isCancelled = true
            val message = Component.text("스폰포인트 설정을 할 수 없습니다. 기지를 이용하세요.")

            event.player.sendActionBar(message)

            bukkitRunnable {
                event.player.sendActionBar(message)
            }.runTaskLater(plugin, 1L)
        }
    }
}