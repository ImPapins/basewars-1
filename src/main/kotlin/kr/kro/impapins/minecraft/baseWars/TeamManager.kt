package kr.kro.impapins.minecraft.baseWars

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.HeightMap
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.World
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.util.UUID
import kotlin.random.Random

object TeamManager {
    val teams = mutableMapOf<String, BaseTeam>()

    private val memberCache = mutableMapOf<UUID, BaseTeam>()
    private val leaderCache = mutableSetOf<UUID>()

    private val file
        get() = File(plugin.dataFolder, "teams.yml")

    fun save() {
        val config = YamlConfiguration()

        for ((id, team) in teams) {
            val path = "teams.$id"

            config.set("$path.name", team.name)
            config.set("$path.color", team.color.toString())
            config.set("$path.leader", team.leader.toString())
            config.set(
                "$path.members",
                team.members.map(UUID::toString)
            )
            team.teamSpawnLocation?.let { location ->
                config.set("$path.teamSpawnLocation", location)
            }
        }

        config.save(file)
    }

    fun load() {
        teams.clear()

        if (!file.exists()) return

        val config = YamlConfiguration.loadConfiguration(file)

        val section = config.getConfigurationSection("teams") ?: return

        for (id in section.getKeys(false)) {
            val path = "teams.$id"

            val name = config.getString("$path.name")!!
            val color = NamedTextColor.NAMES.value(
                config.getString("$path.color")!!.lowercase()
            ) ?: continue

            val leaderString = config.getString("$path.leader")!!

            val leader = runCatching {
                UUID.fromString(leaderString)
            }.getOrElse {
                Bukkit.getOfflinePlayer(leaderString).uniqueId
            }

            val members = mutableSetOf<UUID>()

            for (value in config.getStringList("$path.members")) {
                val uuid = runCatching {
                    UUID.fromString(value)
                }.getOrNull()

                if (uuid != null) {
                    members += uuid
                } else {
                    val offline = Bukkit.getOfflinePlayer(value)
                    members += offline.uniqueId
                }
            }

            val teamSpawnLocation =
                config.getLocation("$path.teamSpawnLocation")
                    ?: randomTeamSpawn(Bukkit.getWorld("world")!!)

            teams[id] = BaseTeam(
                name,
                color,
                leader,
                members,
                teamSpawnLocation
            )
        }

        rebuildCache()
        syncScoreboardTeams()
    }

    private fun rebuildCache() {
        memberCache.clear()
        leaderCache.clear()

        teams.values.forEach { team ->
            leaderCache += team.leader

            team.members.forEach { uuid ->
                memberCache[uuid] = team
            }
        }
    }

    private fun syncScoreboardTeams() {
        val scoreboard = Bukkit.getScoreboardManager().mainScoreboard

        teams.values.forEach { team ->
            val bukkitTeam = scoreboard.getTeam(team.name) ?: scoreboard.registerNewTeam(team.name)

            bukkitTeam.color(team.color)
            bukkitTeam.prefix(Component.text("[${team.name}] ", team.color))
            bukkitTeam.setAllowFriendlyFire(false)
            bukkitTeam.setCanSeeFriendlyInvisibles(false)

            team.members.forEach { uuid ->
                Bukkit.getOfflinePlayer(uuid).name?.let(bukkitTeam::addEntry)
            }
        }
    }

    fun OfflinePlayer.getTeam(): BaseTeam? =
        memberCache[uniqueId]

    fun OfflinePlayer.isLeader(): Boolean =
        uniqueId in leaderCache

    private fun randomTeamSpawn(world: World): Location {
        // plugin.logger.info("randomTeamSpawn start")

        val border = world.worldBorder

        val margin = 100
        val half = (border.size / 2).toInt()

        val minX = (border.center.x - half + margin).toInt()
        val maxX = (border.center.x + half - margin).toInt()
        val minZ = (border.center.z - half + margin).toInt()
        val maxZ = (border.center.z + half - margin).toInt()

        val minChunkX = minX shr 4
        val maxChunkX = maxX shr 4
        val minChunkZ = minZ shr 4
        val maxChunkZ = maxZ shr 4

        val checkedChunks = HashSet<Long>()

        for (i in 0 until 100) {
            val chunkX = Random.nextInt(minChunkX, maxChunkX + 1)
            val chunkZ = Random.nextInt(minChunkZ, maxChunkZ + 1)

            val key = (chunkX.toLong() shl 32) xor (chunkZ.toLong() and 0xffffffffL)
            if (!checkedChunks.add(key))
                continue

            // 청크 로드 (없으면 생성)
            world.getChunkAt(chunkX, chunkZ)
            // plugin.logger.info("$chunkX $chunkZ")

            for (j in 0 until 20) {
                // plugin.logger.info("#$i-$j")

                val x = (chunkX shl 4) + Random.nextInt(16)
                val z = (chunkZ shl 4) + Random.nextInt(16)

                // 월드보더 margin 보정
                if (x !in minX..maxX || z !in minZ..maxZ)
                    continue

                val y = world.getHighestBlockYAt(
                    x,
                    z,
                    HeightMap.MOTION_BLOCKING_NO_LEAVES
                )

                val biome = world.getBiome(x, y, z)
                val biomeName = biome.key().value()

                if ("ocean" in biomeName || "river" in biomeName) {
                    // plugin.logger.info(biomeName)
                    break
                }

                val ground = world.getBlockAt(x, y, z)
                val feet = world.getBlockAt(x, y + 1, z)
                val head = world.getBlockAt(x, y + 2, z)

                // plugin.logger.info(
                //     "ground=${ground.type}, feet=${feet.type}, head=${head.type}, biome=$biomeName"
                // )

                if (!ground.type.isSolid) continue
                if (!feet.isPassable || !head.isPassable) continue
                if (ground.isLiquid || feet.isLiquid || head.isLiquid) continue

                return Location(
                    world,
                    x + 0.5,
                    y + 1.0,
                    z + 0.5
                )
            }
        }

        // plugin.logger.info("cant find")
        return world.spawnLocation
    }
}