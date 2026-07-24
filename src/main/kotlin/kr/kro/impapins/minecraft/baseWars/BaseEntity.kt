package kr.kro.impapins.minecraft.baseWars

import kr.kro.impapins.minecraft.baseWars.TeamManager.getTeam
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Interaction
import org.bukkit.entity.TextDisplay
import org.bukkit.inventory.Inventory
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID

data class BaseEntity(
    val id: UUID,
    val location: Location,
    var info: BaseInfo,

    var block: BlockDisplay? = null,
    var text: TextDisplay? = null,
    var marker: ArmorStand? = null,
    var interaction: Interaction? = null,

    var storageInventory: Inventory? = null
) {
    private var alarmCooldown = 0
    private val detectedEnemies = mutableSetOf<UUID>()

    fun checkAlarm() {
        if (info.level < 4) return

        val range = if (info.level >= 6) 100.0 else 50.0

        val enemies = Bukkit.getOnlinePlayers()
            .filter {
                val team = it.getTeam()
                team != null && team.name != info.baseTeam
            }
            .filter {
                it.world == location.world
            }
            .filter {
                it.location.isWithinXZ(location, range)
            }

        // 발광 효과
        enemies.forEach {
            it.addPotionEffect(
                PotionEffect(
                    PotionEffectType.GLOWING,
                    20,
                    0,
                    true,
                    false,
                    true
                )
            )
        }

        val currentEnemies = enemies
            .map { it.uniqueId }
            .toSet()

        // 적이 모두 사라짐
        if (currentEnemies.isEmpty()) {
            detectedEnemies.clear()
            alarmCooldown = 0
            return
        }

        // 새로 들어온 적
        val newEnemies = enemies.filter {
            it.uniqueId !in detectedEnemies
        }

        val hasNewEnemy = newEnemies.isNotEmpty()

        // 현재 감지 목록 갱신
        detectedEnemies.clear()
        detectedEnemies.addAll(currentEnemies)

        // 아군에게는 새 적이 들어왔거나 일정 시간마다 알림
        if (hasNewEnemy || alarmCooldown <= 0) {

            alarmCooldown = 5

            val teamMessage = Component.text(
                "${info.name} 근처 ${range.toInt()}칸에 적 ${currentEnemies.size}명이 침입했습니다!",
                NamedTextColor.RED
            )

            Bukkit.getOnlinePlayers()
                .filter {
                    val team = it.getTeam()
                    team != null && team.name == info.baseTeam
                }
                .forEach {
                    it.sendMessage(teamMessage)

                    it.playSound(
                        it.location,
                        Sound.BLOCK_NOTE_BLOCK_PLING,
                        1f,
                        0.5f
                    )
                }
        } else {
            alarmCooldown--
        }

        // 적에게는 처음 들어왔을 때만 알림
        if (hasNewEnemy) {
            val enemyMessage = Component.text(
                "근처 기지의 알람이 발동했습니다! 발광이 부여됩니다.",
                NamedTextColor.YELLOW
            )

            newEnemies.forEach {
                it.sendMessage(enemyMessage)

                it.playSound(
                    it.location,
                    Sound.BLOCK_NOTE_BLOCK_BELL,
                    1f,
                    1.5f
                )
            }
        }
    }

    private val trappedPlayers = mutableSetOf<UUID>()

    fun checkTrap() {
        if (info.level < 5) return

        val range = if (info.level >= 7) 25.0 else 10.0

        val enemies = Bukkit.getOnlinePlayers().filter {
            val team = it.getTeam()
            team != null && team.name != info.baseTeam
        }.filter {
            it.world == location.world
        }.filter {
            it.location.isWithinXZ(location, range)
        }

        val currentEnemies = enemies
            .map { it.uniqueId }
            .toSet()

        // 함정 범위를 벗어난 플레이어 제거
        trappedPlayers.retainAll(currentEnemies)

        enemies.forEach { enemy ->
            val isNew = trappedPlayers.add(enemy.uniqueId)

            enemy.addPotionEffect(
                PotionEffect(
                    PotionEffectType.SLOWNESS,
                    60,
                    1,      // 구속 II
                    true,
                    false,
                    true
                )
            )

            enemy.addPotionEffect(
                PotionEffect(
                    PotionEffectType.WEAKNESS,
                    60,
                    0,      // 나약함 I
                    true,
                    false,
                    true
                )
            )

            enemy.addPotionEffect(
                PotionEffect(
                    PotionEffectType.MINING_FATIGUE,
                    60,
                    0,      // 채굴 피로 I
                    true,
                    false,
                    true
                )
            )

            if (isNew) {
                enemy.sendMessage(
                    Component.text(
                        "함정이 발동했습니다! 디버프를 받습니다.",
                        NamedTextColor.DARK_RED
                    )
                )

                enemy.playSound(
                    enemy.location,
                    Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE,
                    1f,
                    0.8f
                )
            }
        }
    }

    fun isLoaded() = block != null
}