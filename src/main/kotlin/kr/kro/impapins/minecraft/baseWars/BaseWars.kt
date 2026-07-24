@file:Suppress("UnstableApiUsage")
package kr.kro.impapins.minecraft.baseWars

import io.papermc.paper.command.brigadier.BasicCommand
import org.bukkit.Bukkit
import org.bukkit.Difficulty
import org.bukkit.GameRules
import org.bukkit.Location
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class BaseWars : JavaPlugin() {
    override fun onLoad() {
        plugin = this

        // exportDialogDataPack()
    }

    override fun onEnable() {
        val world = Bukkit.getWorld("world")!!
        world.spawnLocation = Location(
            world,
            0.0,
            world.getHighestBlockAt(0, 0).y.toDouble(),
            0.0
        )

        Bukkit.getWorlds().forEach {
            it.setGameRule(GameRules.SEND_COMMAND_FEEDBACK, false)
            it.setGameRule(GameRules.LOCATOR_BAR, true)
            it.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, false)
            it.setGameRule(GameRules.IMMEDIATE_RESPAWN, true)
            it.setGameRule(GameRules.REDUCED_DEBUG_INFO, true)
            it.setGameRule(GameRules.RESPAWN_RADIUS, 0)
            it.difficulty = Difficulty.HARD
            it.worldBorder.size = 10000.0
        }

        val listeners = listOf(
            CombatManager,
            PlayerListener,
            BaseManager,
            MarkerManager,
            TeamChatListener,
            RecipeManager,
            ElytraListener
        )

        listeners.forEach {
            server.pluginManager.registerEvents(
                it, this
            )
        }

        data class CommandInfo(
            val label: String,
            val aliases: List<String>,
            val command: BasicCommand
        )

        val commands = listOf(
            // CommandInfo("base", listOf(), BaseCommand),
            CommandInfo("ac", listOf("allchat", "전체채팅", "ㅁㅊ", "전챗", "ㅈㅊ"), AllChatCommand),
            CommandInfo("basemenu", listOf("bm", "기지메뉴", "ㅠㅡ", "메뉴", "menu", "m", "ㅡ"), BaseMenuCommand)
        )

        commands.forEach {
            registerCommand(
                it.label,
                it.aliases,
                it.command
            )
        }

        plugin.dataFolder.mkdirs()

        TeamManager.load()
        RecipeManager.init()
        BaseManager.init()
        MarkerManager.init()
        BaseCompassManager.init()
    }

    override fun onDisable() {
        TeamManager.save()
        BaseManager.save()

        MarkerManager.removeAll()
        BaseManager.removeAll()
        BaseCompassManager.removeAll()

        BaseManager.saveStorage()
    }

    private fun exportDialogDataPack() {
        val targetDir = File(server.worldContainer, "world/datapacks/BaseWarsPack")

        try {
            copyResourceFolder(targetDir)
            logger.info("데이터팩 복사 완료")
        } catch (e: Exception) {
            logger.severe("데이터팩 복사 실패")
            e.printStackTrace()
        }
    }

    private fun copyResourceFolder(targetDir: File) {
        val resourcePath = "datapack"
        val uri = javaClass.classLoader.getResource(resourcePath)?.toURI()
            ?: throw IllegalArgumentException("리소스를 찾을 수 없음: $resourcePath")

        when (uri.scheme) {
            "file" -> {
                Files.walk(Path.of(uri)).forEach { source ->
                    val relative = Path.of(uri).relativize(source)
                    val target = targetDir.toPath().resolve(relative)

                    if (Files.isDirectory(source)) {
                        Files.createDirectories(target)
                    } else {
                        Files.createDirectories(target.parent)
                        Files.copy(
                            source,
                            target,
                            StandardCopyOption.REPLACE_EXISTING
                        )
                    }
                }
            }

            "jar" -> {
                val fs = try {
                    FileSystems.getFileSystem(uri)
                } catch (_: Exception) {
                    FileSystems.newFileSystem(uri, emptyMap<String, Any>())
                }

                val root = fs.getPath(resourcePath)

                Files.walk(root).forEach { source ->
                    val relative = root.relativize(source)
                    val target = targetDir.toPath().resolve(relative.toString())

                    if (Files.isDirectory(source)) {
                        Files.createDirectories(target)
                    } else {
                        Files.createDirectories(target.parent)
                        Files.copy(
                            source,
                            target,
                            StandardCopyOption.REPLACE_EXISTING
                        )
                    }
                }
            }

            else -> error("지원하지 않는 URI: ${uri.scheme}")
        }
    }
}
