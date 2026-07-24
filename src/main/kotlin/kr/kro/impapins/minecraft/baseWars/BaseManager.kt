@file:Suppress("DEPRECATION")

package kr.kro.impapins.minecraft.baseWars

import com.destroystokyo.paper.profile.PlayerProfile
import io.papermc.paper.ban.BanListType
import kr.kro.impapins.minecraft.baseWars.BaseCompassManager.remove
import kr.kro.impapins.minecraft.baseWars.CombatManager.isCombating
import kr.kro.impapins.minecraft.baseWars.MarkerManager.removeMarker
import kr.kro.impapins.minecraft.baseWars.TeamChatListener.hasChatted
import kr.kro.impapins.minecraft.baseWars.TeamManager.getTeam
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import org.bukkit.BanEntry
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.Interaction
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.UUID

object BaseManager : Listener {
    val bases: MutableMap<UUID, BaseEntity> = mutableMapOf()
    val pendingRespawn = mutableSetOf<UUID>()

    const val BASE_MAX_LEVEL = 7
    val baseHp: List<Double> = listOf(
        0.0,
        100.0,
        300.0,
        500.0,
        1000.0,
        2000.0,
        3000.0,
        5000.0
    )

    val baseStorageSize: List<Int> = listOf(
        0,
        0,
        9,
        18,
        27,
        36,
        45,
        54
    )

    val baseUpgradeCost: List<List<ItemStack>> = listOf(
        emptyList(), // 0

        listOf( // 1 -> 2
            ItemStack(Material.COPPER_INGOT, 10)
        ),

        listOf( // 2 -> 3
            ItemStack(Material.COPPER_INGOT, 64)
        ),

        listOf( // 3 -> 4
            ItemStack(Material.COPPER_BLOCK, 10),
            ItemStack(Material.IRON_BLOCK, 3)
        ),

        listOf( // 4 -> 5
            ItemStack(Material.COPPER_BLOCK, 32),
            ItemStack(Material.IRON_BLOCK, 5),
            ItemStack(Material.DIAMOND, 10)
        ),

        listOf( // 5 -> 6
            ItemStack(Material.IRON_BLOCK, 10),
            ItemStack(Material.DIAMOND, 32)
        ),

        listOf( // 6 -> 7
            ItemStack(Material.DIAMOND, 64),
            ItemStack(Material.NETHERITE_INGOT, 3)
        ),

        emptyList() // 7은 최대 레벨
    )

    val baseBlock: List<Material> = listOf(
        Material.AIR,
        Material.COPPER_BLOCK,
        Material.IRON_BLOCK,
        Material.GOLD_BLOCK,
        Material.DIAMOND_BLOCK,
        Material.EMERALD_BLOCK,
        Material.NETHERITE_BLOCK,
        Material.BEACON
    )

    private const val BASE_RADIUS = 100.0
    private const val POINT_GAP = 2.0

    private val circleOffsets = buildList {
        val pointCount = ((2 * Math.PI * BASE_RADIUS) / POINT_GAP).toInt()

        repeat(pointCount) { i ->
            val angle = i * 2.0 * Math.PI / pointCount

            add(
                kotlin.math.cos(angle) * BASE_RADIUS to
                        kotlin.math.sin(angle) * BASE_RADIUS
            )
        }
    }

    private var circleIndex = 0
    private const val POINTS_PER_TICK = 80

    private val file get() = File(plugin.dataFolder, "base.yml")

    private const val BASE_MIN_DISTANCE = 100.0

    private const val COPPER_INGOT_HEAL = 10.0
    private const val COPPER_BLOCK_HEAL = 100.0

    val renaming = mutableMapOf<UUID, BaseEntity>()
    private val renameTasks = mutableMapOf<UUID, BukkitTask>()

    fun save() {
        val config = YamlConfiguration()

        for ((id, base) in bases) {
            val path = "bases.$id"

            config.set("$path.world", base.location.world.name)
            config.set("$path.x", base.location.x)
            config.set("$path.y", base.location.y)
            config.set("$path.z", base.location.z)

            config.set("$path.baseTeam", base.info.baseTeam)
            config.set("$path.name", base.info.name)
            config.set("$path.level", base.info.level)
            config.set("$path.hp", base.info.hp)
            config.set("$path.inventory", base.info.inventoryEncoded.encode())
            config.set("$path.spawnPlayers", base.info.spawnPlayers.map(UUID::toString))
            config.set("$path.spawnLocation", base.info.spawnLocation)
        }

        config.set("pendingRespawn", pendingRespawn.map(UUID::toString))

        config.save(file)
    }

    fun load() {
        bases.clear()

        if (!file.exists()) return

        val config = YamlConfiguration.loadConfiguration(file)

        pendingRespawn.clear()

        config.getStringList("pendingRespawn")
            .map(UUID::fromString)
            .forEach(pendingRespawn::add)

        val section = config.getConfigurationSection("bases") ?: return

        for (idString in section.getKeys(false)) {
            val path = "bases.$idString"

            val world = Bukkit.getWorld(config.getString("$path.world")!!) ?: continue

            val location = Location(
                world,
                config.getDouble("$path.x"),
                config.getDouble("$path.y"),
                config.getDouble("$path.z")
            )

            val info = BaseInfo(
                baseTeam = config.getString("$path.baseTeam")!!,
                name = config.getString("$path.name")!!,
                level = config.getInt("$path.level"),
                inventoryEncoded = config.getString("$path.inventory")!!.decode(),
                hp = config.getDouble("$path.hp"),
                spawnLocation = config.getLocation("$path.spawnLocation")!!,
                spawnPlayers = config.getStringList("$path.spawnPlayers")
                    .map(UUID::fromString)
                    .toMutableSet()
            )

            val base = BaseEntity(
                UUID.fromString(idString),
                location,
                info
            )

            bases[base.id] = base

            location.chunk.isForceLoaded = true
            spawnDisplay(base)
        }
    }

    fun init() {
        load()

        bukkitRunnable {
            for (player in Bukkit.getOnlinePlayers()) {
                for (base in bases.values) {
                    val block = base.block ?: continue
                    val center = block.location

                    if (player.world != center.world) continue

                    val marker = base.marker ?: continue
                    val visible = base.info.baseTeam == player.getTeam()?.name ||
                            player.location.isWithinXZ(center, 100.0)

                    if (visible) {
                        player.showEntity(plugin, marker)
                    } else {
                        player.hideEntity(plugin, marker)
                    }

                    if (!player.location.isWithinXZ(center, 250.0)) {
                        continue
                    }

                    val playerY = player.location.y
                    var y = playerY - 5.0

                    while (y <= playerY + 5.0) {
                        repeat(POINTS_PER_TICK) {
                            val index = (circleIndex + it) % circleOffsets.size
                            val (x, z) = circleOffsets[index]

                            player.spawnParticle(
                                Particle.END_ROD,
                                center.x + x,
                                y,
                                center.z + z,
                                1,
                                0.0,
                                0.0,
                                0.0,
                                0.0,
                                null,
                                true
                            )
                        }
                        y += 1.0
                    }

                    base.info.let {
                        val dust = Particle.DustOptions(
                            if (player.uniqueId in it.spawnPlayers) {
                                Color.BLUE
                            } else {
                                Color.RED
                            },
                            1.0f
                        )

                        val spawn = it.spawnLocation

                        val step = 0.15

                        var x = -1.0
                        while (x <= 1.0) {
                            var z = -1.0
                            while (z <= 1.0) {
                                if (x * x + z * z <= 1.0) {
                                    player.spawnParticle(
                                        Particle.DUST,
                                        spawn.x + x,
                                        spawn.y + 0.05,
                                        spawn.z + z,
                                        1,
                                        0.0,
                                        0.0,
                                        0.0,
                                        0.0,
                                        dust,
                                        true
                                    )
                                }
                                z += step
                            }
                            x += step
                        }
                    }

                    base.text?.text(
                        Component.text()
                            .append(Component.text(base.info.baseTeam))
                            .append(Component.newline())
                            .append(Component.text(base.info.name))
                            .append(Component.newline())
                            .append(Component.text("레벨: ${base.info.level}"))
                            .append(Component.newline())
                            .append(
                                Component.text(
                                    String.format(
                                        "%.2f/%.2f",
                                        base.info.hp,
                                        baseHp[base.info.level]
                                    )
                                )
                            )
                            .build()
                    )
                }
            }

            circleIndex = (circleIndex + POINTS_PER_TICK) % circleOffsets.size
        }.runTaskTimer(plugin, 0L, 1L)

        bukkitRunnable {
            for (player in Bukkit.getOnlinePlayers()) {
                if (getSpawnBase(player) != null) continue

                player.playSound(
                    player.location,
                    Sound.BLOCK_ANVIL_LAND,
                    2.0f,
                    1.0f
                )

                player.sendMessage(
                    Component.text(
                        """[경고] 현재 체크포인트가 설정 되어있지 않습니다.
                        |[경고] 기지 메뉴에서 스폰포인트를 설정하세요.""".trimMargin(),
                        NamedTextColor.RED
                    )
                )

                player.showTitle(
                    Title.title(
                        Component.text("! 경고 !", NamedTextColor.RED),
                        Component.text("현재 체크포인트가 설정 되어있지 않습니다.", NamedTextColor.RED),
                        Title.Times.times(
                            Duration.ofMillis(500),
                            Duration.ofMillis(1000),
                            Duration.ofMillis(500)
                        )
                    )
                )
            }
        }.runTaskTimer(plugin, 5 * 60 * 20L, 10 * 60 * 20L)

        bukkitRunnable {
            bases.values.forEach {
                it.checkAlarm()
                it.checkTrap()
            }
        }.runTaskTimer(plugin, 20L, 20L)
    }

    fun newBase(location: Location, info: BaseInfo): BaseEntity {
        location.chunk.isForceLoaded = true

        val base = BaseEntity(
            UUID.randomUUID(),
            location,
            info
        )

        bases[base.id] = base

        spawnDisplay(base)

        save()

        return base
    }

    fun removeBase(base: BaseEntity, name: String) {
        base.location.chunk.isForceLoaded = false

        // 이 기지 관련 GUI 닫기
        Bukkit.getOnlinePlayers().forEach { player ->
            val inventory = player.openInventory.topInventory
            val holder = inventory.holder

            when (holder) {
                is BaseMenuHolder -> {
                    if (holder.base == base) {
                        player.closeInventory()
                    }
                }

                is StorageHolder -> {
                    if (holder.base == base) {
                        player.closeInventory()
                    }
                }

                is RepairMenuHolder -> {
                    if (holder.base == base) {
                        player.closeInventory()
                    }
                }
            }
        }

        Bukkit.broadcast(
            Component.text(
                "${base.info.baseTeam} 팀의 기지가 ${name}에게 파괴되었습니다!",
                NamedTextColor.RED
            )
        )

        Bukkit.getOnlinePlayers().forEach { player ->
            player.playSound(
                player.location,
                Sound.ENTITY_ENDER_DRAGON_DEATH,
                1f,
                1f
            )
        }


        val inventory =
            base.storageInventory
                ?: if (base.info.inventoryEncoded.isNotEmpty()) {
                    base.info.inventoryEncoded.toInventory()
                } else {
                    null
                }


        if (inventory != null) {
            base.info.inventoryEncoded = inventory.toStringList()

            inventory.storageContents
                .filterNotNull()
                .forEach { item ->
                    base.location.world.dropItemNaturally(
                        base.location,
                        item
                    )
                }
        }

        removeDisplay(base)

        base.storageInventory = null

        bases.remove(base.id)
        pendingRespawn.removeAll(base.info.spawnPlayers)

        save()
    }

    fun upgrade(base: BaseEntity, player: Player): Component? {
        if (base.info.level >= BASE_MAX_LEVEL) {
            return Component.text("이미 최대 레벨입니다.")
        }

        val cost = baseUpgradeCost[base.info.level]
        val inventory = player.inventory

        if (cost.any { !inventory.containsAtLeast(it, it.amount) }) {
            return Component.text("인벤토리에 업그레이드 재료가 부족합니다.")
        }

        cost.forEach {
            inventory.removeItem(it.clone())
        }


        base.info.level++
        base.info.hp = baseHp[base.info.level]


        val newSize = baseStorageSize[base.info.level]


        if (newSize > 0) {

            val oldInventory =
                if (base.info.inventoryEncoded.isEmpty()) {
                    null
                } else {
                    base.storageInventory
                        ?: base.info.inventoryEncoded.toInventory()
                }


            val newInventory = Bukkit.createInventory(
                StorageHolder(base),
                newSize,
                Component.text("창고")
            )


            if (oldInventory != null) {
                for (i in 0 until minOf(oldInventory.size, newInventory.size)) {
                    newInventory.setItem(
                        i,
                        oldInventory.getItem(i)
                    )
                }
            }


            base.storageInventory = newInventory
            base.info.inventoryEncoded = newInventory.toStringList()


            // 창고를 보고 있던 플레이어 갱신
            oldInventory?.viewers
                ?.filterIsInstance<Player>()
                ?.forEach {
                    it.openInventory(newInventory)
                }


        } else {
            base.info.inventoryEncoded = emptyList()
        }


        base.block?.block =
            baseBlock[base.info.level].createBlockData()

        save()

        return null
    }

    fun spawnDisplay(base: BaseEntity) {
        if (base.isLoaded()) return

        val world = base.location.world

        base.block = world.spawn(base.location, BlockDisplay::class.java).apply {
            block = baseBlock[base.info.level].createBlockData()
            isGlowing = true
        }

        base.text = world.spawn(
            base.location.clone().add(0.5, 1.5, 0.5),
            TextDisplay::class.java
        ).apply {
            billboard = Display.Billboard.CENTER
            isSeeThrough = true
            isShadowed = false
            lineWidth = 200
        }

        base.interaction = world.spawn(
            base.location.clone().add(0.5, -0.25, 0.5),
            Interaction::class.java
        ) {
            it.interactionWidth = 1.5f
            it.interactionHeight = 1.5f
            it.isResponsive = true
        }

        base.marker = world.spawn(base.location, ArmorStand::class.java) {
            it.isInvisible = true
            it.isMarker = true
            it.isInvulnerable = true
            it.setGravity(false)
            it.isSilent = true
        }

        for (player in Bukkit.getOnlinePlayers()) {
            player.hideEntity(plugin, base.marker!!)
        }

        val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
        val team = scoreboard.getTeam("baseTeam")
            ?: scoreboard.registerNewTeam("baseTeam")

        team.color(NamedTextColor.WHITE)
        team.addEntity(base.marker!!)

        base.marker?.getAttribute(Attribute.WAYPOINT_TRANSMIT_RANGE)
            ?.baseValue = 30000.0
    }

    fun removeDisplay(base: BaseEntity) {
        val team = Bukkit.getScoreboardManager()
            .mainScoreboard
            .getTeam("baseTeam")

        base.marker?.let {
            team?.removeEntity(it)
        }

        base.block?.remove()
        base.text?.remove()
        base.marker?.remove()
        base.interaction?.remove()

        base.block = null
        base.text = null
        base.marker = null
        base.interaction = null
    }

    fun removeAll() {
        for ((_, base) in bases) {
            removeDisplay(base)
        }
    }

    fun rename(player: Player, base: BaseEntity): Boolean {
        if (renaming.containsKey(player.uniqueId)) {
            return false
        }

        renaming[player.uniqueId] = base

        val task = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (renaming.remove(player.uniqueId) != null) {
                renameTasks.remove(player.uniqueId)
                player.sendMessage("기지 이름 설정이 취소되었습니다.")
            }
        }, 20L * 10)

        renameTasks[player.uniqueId] = task

        player.closeInventory()
        player.sendMessage("10초 안에 채팅으로 기지 이름을 입력하세요.")

        return true
    }

    fun openBaseList(player: Player) {
        val team = player.getTeam() ?: return

        val teamBases = bases.values.filter {
            it.info.baseTeam == team.name
        }

        val rows = ((teamBases.size + 8) / 9).coerceIn(1, 6)

        val inventory = Bukkit.createInventory(
            BaseListHolder(),
            rows * 9,
            Component.text("기지 메뉴")
        )

        teamBases.forEachIndexed { index, base ->
            inventory.setItem(
                index,
                ItemStack(baseBlock[base.info.level]).apply {
                    editMeta {
                        it.displayName(
                            Component.text(
                                base.info.name
                            )
                                .decoration(TextDecoration.ITALIC, false)
                        )

                        it.lore(
                            listOf(
                                Component.text("레벨: ${base.info.level}", NamedTextColor.GRAY)
                                    .decoration(TextDecoration.ITALIC, false),
                                Component.text(
                                    String.format(
                                        "체력: %.2f/%.2f",
                                        base.info.hp,
                                        baseHp[base.info.level]
                                    ),
                                    NamedTextColor.RED
                                )
                                    .decoration(TextDecoration.ITALIC, false)
                            )
                        )

                        it.persistentDataContainer.set(
                            makeKey("baseId"),
                            PersistentDataType.STRING,
                            base.id.toString()
                        )
                    }
                }
            )
        }

        player.openInventory(inventory)
    }

    @EventHandler
    fun onChat(event: AsyncPlayerChatEvent) {
        val base = renaming.remove(event.player.uniqueId) ?: return

        renameTasks.remove(event.player.uniqueId)?.cancel()

        event.isCancelled = true

        val name = event.message.trim()

        Bukkit.getScheduler().runTask(plugin,
            Runnable {
                base.info.name = name
                save()
                event.player.sendMessage("기지 이름이 '$name'으로 변경되었습니다.")
            }
        )
    }

    @EventHandler
    fun onBaseItemInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (event.action.isLeftClick) return

        val item = event.item ?: return
        if (!item.hasTag(makeKey("baseItem"))) return

        val player = event.player
        val block = player.location.clone().add(0.0, 1.0, 0.0).block
        val location = block.location

        if (bases.values.any { it.location.isWithinXZ(location, BASE_MIN_DISTANCE) }) {
            player.sendMessage(
                Component.text(
                    "다른 기지와 너무 가깝습니다.",
                    NamedTextColor.RED
                )
            )
            event.isCancelled = true
            return
        }

        val base = newBase(
            location,
            BaseInfo(
                baseTeam         = player.getTeam()!!.name,
                level            = 1,
                inventoryEncoded = emptyList(),
                name             = "${player.getTeam()!!.name} 기지",
                hp               = baseHp[1],
                spawnLocation    = location.clone().add(0.5, -1.0, 0.5)
            )
        )

        rename(player, base)

        if (player.gameMode != GameMode.CREATIVE) {
            item.amount--
        }

        event.isCancelled = true
    }

    @EventHandler
    fun onBaseInteract(event: PlayerInteractAtEntityEvent) {
        if (event.hand != EquipmentSlot.HAND) return

        val interaction = event.rightClicked as? Interaction ?: return
        val base = bases.values.firstOrNull {
            it.interaction?.uniqueId == interaction.uniqueId
        } ?: return

        if (base.info.baseTeam != event.player.getTeam()?.name) {
            return
        }

        event.isCancelled = true
        openBaseMenu(event.player, base)
    }

    fun openBaseMenu(player: Player, base: BaseEntity) {
        if (isCombating(player.uniqueId)) {
            player.sendMessage("싸우고 있는 동안에는 기지 메뉴를 열 수 없습니다.")
            return
        }

        val inventory = Bukkit.createInventory(
            BaseMenuHolder(base),
            45,
            Component.text(base.info.name)
        )

        updateBaseMenu(inventory, base)

        player.openInventory(inventory)
    }

    fun updateBaseMenu(base: BaseEntity) {
        Bukkit.getOnlinePlayers().forEach { player ->

            val inventory = player.openInventory.topInventory
            val holder = inventory.holder

            if (holder is BaseMenuHolder && holder.base == base) {
                updateBaseMenu(inventory, base)
            }
        }
    }

    fun updateBaseMenu(inv: Inventory, base: BaseEntity) {
        inv.clear()

        inv.setItem(
            30,
            ItemStack(Material.NAME_TAG).apply {
                editMeta {
                    it.displayName(
                        Component.text("기지 이름 변경")
                            .decoration(TextDecoration.ITALIC, false)
                    )
                }
            }
        )

        inv.setItem(
            34,
            ItemStack(Material.RESPAWN_ANCHOR).apply {
                editMeta {
                    it.displayName(
                        Component.text("스폰 설정")
                            .decoration(TextDecoration.ITALIC, false)
                    )
                }
            }
        )

        inv.setItem(
            28,
            if (base.info.inventoryEncoded.isNotEmpty()) {
                ItemStack(Material.CHEST).apply {
                    editMeta {
                        it.displayName(
                            Component.text("창고")
                                .decoration(TextDecoration.ITALIC, false)
                        )
                    }
                }
            } else {
                ItemStack(Material.BARRIER).apply {
                    editMeta {
                        it.displayName(
                            Component.text("창고", NamedTextColor.RED)
                                .decoration(TextDecoration.ITALIC, false)
                        )

                        it.lore(
                            listOf(
                                Component.text("창고를 사용할 수 없습니다.", NamedTextColor.RED)
                                    .decoration(TextDecoration.ITALIC, false),

                                Component.text("창고는 2레벨 이상부터 사용 가능합니다.", NamedTextColor.RED)
                                    .decoration(TextDecoration.ITALIC, false)
                            )
                        )
                    }
                }
            }
        )

        inv.setItem(
            13,
            ItemStack(baseBlock[base.info.level]).apply {
                val lore = if (base.info.level >= BASE_MAX_LEVEL) {
                    listOf(
                        Component.text("최대 레벨입니다.")
                            .decoration(TextDecoration.ITALIC, false)
                    )
                } else {
                    buildList {
                        fun line(text: String) =
                            Component.text(text, NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false)

                        fun value(text: String) =
                            Component.text(text, NamedTextColor.YELLOW)
                                .decoration(TextDecoration.ITALIC, false)

                        fun header(text: String) =
                            Component.text(text, NamedTextColor.GOLD)
                                .decoration(TextDecoration.ITALIC, false)

                        fun danger(text: String) =
                            Component.text(text, NamedTextColor.RED)
                                .decoration(TextDecoration.ITALIC, false)

                        fun trap(text: String) =
                            Component.text(text, NamedTextColor.LIGHT_PURPLE)
                                .decoration(TextDecoration.ITALIC, false)

                        when (base.info.level + 1) {
                            2 -> {
                                add(line("▶ 창고가 ").append(value("9칸")).append(line("으로 확장됩니다.")))
                                add(line("▶ 기지 체력이 ").append(value("300")).append(line("이 됩니다.")))
                            }

                            3 -> {
                                add(line("▶ 창고가 ").append(value("18칸")).append(line("으로 확장됩니다.")))
                                add(line("▶ 기지 체력이 ").append(value("500")).append(line("이 됩니다.")))
                            }

                            4 -> {
                                add(line("▶ 창고가 ").append(value("27칸")).append(line("으로 확장됩니다.")))
                                add(line("▶ 기지 체력이 ").append(value("1000")).append(line("이 됩니다.")))
                                add(danger("▶ 알람이 추가됩니다."))
                                add(Component.text("  • 반경 50칸 적 감지", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false))
                                add(Component.text("  • 팀원 전체에게 알림", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false))
                                add(Component.text("  • 적에게 발광 효과", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false))
                            }

                            5 -> {
                                add(line("▶ 창고가 ").append(value("36칸")).append(line("으로 확장됩니다.")))
                                add(line("▶ 기지 체력이 ").append(value("2000")).append(line("이 됩니다.")))
                                add(trap("▶ 함정이 추가됩니다."))
                                add(Component.text("  • 반경 10칸 적에게 구속, 나약함", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false))
                            }

                            6 -> {
                                add(line("▶ 창고가 ").append(value("45칸")).append(line("으로 확장됩니다.")))
                                add(line("▶ 기지 체력이 ").append(value("3000")).append(line("이 됩니다.")))
                                add(danger("▶ 알람 범위가 100칸으로 증가합니다."))
                            }

                            7 -> {
                                add(line("▶ 창고가 ").append(value("54칸")).append(line("으로 확장됩니다.")))
                                add(line("▶ 기지 체력이 ").append(value("5000")).append(line("이 됩니다.")))
                                add(trap("▶ 함정 범위가 25칸으로 증가합니다."))
                            }
                        }

                        add(Component.empty())

                        add(header("필요 재료"))

                        baseUpgradeCost[base.info.level].forEach {
                            add(
                                Component.text()
                                    .append(Component.translatable(it.translationKey()).color(NamedTextColor.GRAY))
                                    .append(Component.text(" x${it.amount}", NamedTextColor.YELLOW))
                                    .decoration(TextDecoration.ITALIC, false)
                                    .build()
                            )
                        }
                    }
                }

                editMeta {
                    it.displayName(
                        Component.text("기지 업그레이드")
                            .decoration(TextDecoration.ITALIC, false)
                    )

                    it.lore(lore)
                }
            }
        )

        inv.setItem(
            33,
            ItemStack(Material.WHITE_BED).apply {
                editMeta {
                    it.displayName(
                        Component.text("기지 스폰 위치 설정")
                            .decoration(TextDecoration.ITALIC, false)
                    )
                }
            }
        )

        inv.setItem(
            32,
            ItemStack(Material.TOTEM_OF_UNDYING).apply {
                editMeta {
                    it.displayName(
                        Component.text("사망한 팀원 부활", NamedTextColor.WHITE)
                            .decoration(TextDecoration.ITALIC, false)
                    )
                }
            }
        )

        inv.setItem(
            31,
            ItemStack(Material.ENDER_PEARL).apply {
                editMeta {
                    it.displayName(
                        Component.text("기지로 이동")
                            .decoration(TextDecoration.ITALIC, false)
                    )
                }
            }
        )

        inv.setItem(
            29,
            ItemStack(Material.ANVIL).apply {
                editMeta {
                    it.displayName(
                        Component.text("기지 수리")
                            .decoration(TextDecoration.ITALIC, false)
                    )
                }
            }
        )

        inv.setItem(
            44,
            ItemStack(Material.BARRIER).apply {
                editMeta {
                    it.displayName(
                        Component.text("닫기")
                            .decoration(TextDecoration.ITALIC, false)
                    )
                }
            }
        )
    }

    fun openRepairMenu(
        player: Player,
        base: BaseEntity
    ) {
        val inv = Bukkit.createInventory(
            RepairMenuHolder(base),
            27,
            Component.text("기지 수리")
        )

        updateRepairMenu(inv, player, base)
        player.openInventory(inv)
    }

    fun updateRepairMenu(
        inv: Inventory,
        player: Player,
        base: BaseEntity
    ) {
        inv.clear()

        val maxHp = baseHp[base.info.level]
        val missingHp = maxHp - base.info.hp

        val ingots = player.inventory
            .all(Material.COPPER_INGOT)
            .values
            .sumOf { it.amount }

        val blocks = player.inventory
            .all(Material.COPPER_BLOCK)
            .values
            .sumOf { it.amount }

        fun text(text: String, color: NamedTextColor) =
            Component.text(text, color)
                .decoration(TextDecoration.ITALIC, false)

        // 현재 기지 상태
        inv.setItem(
            22,
            ItemStack(baseBlock[base.info.level]).apply {
                editMeta {
                    it.displayName(
                        text("기지 상태", NamedTextColor.GOLD)
                    )

                    it.lore(
                        listOf(
                            text("현재 체력", NamedTextColor.GRAY),
                            text(
                                "${base.info.hp} / $maxHp",
                                NamedTextColor.RED
                            ),
                            Component.empty(),
                            text(
                                "레벨 ${base.info.level}",
                                NamedTextColor.AQUA
                            )
                        )
                    )
                }
            }
        )

        if (missingHp <= 0.0) {
            fun barrier(name: String): ItemStack =
                ItemStack(Material.BARRIER).apply {
                    editMeta {
                        it.displayName(
                            text(name, NamedTextColor.RED)
                        )

                        it.lore(
                            listOf(
                                text(
                                    "현재 체력이 최대입니다.",
                                    NamedTextColor.GRAY
                                )
                            )
                        )
                    }
                }

            inv.setItem(10, barrier("구리 주괴로 수리"))
            inv.setItem(12, barrier("구리 주괴 모두 사용"))
            inv.setItem(14, barrier("구리 블록으로 수리"))
            inv.setItem(16, barrier("구리 블록 모두 사용"))

        } else {

            fun repairItem(
                material: Material,
                amount: Int,
                title: String,
                owned: Int,
                heal: Double,
                all: Boolean
            ): ItemStack {

                return ItemStack(material).apply {

                    this.amount = amount.coerceIn(1, 64)

                    editMeta {

                        it.displayName(
                            text(title, NamedTextColor.GOLD)
                        )

                        it.lore(
                            listOf(
                                text(
                                    "보유 : ${owned}개",
                                    NamedTextColor.GRAY
                                ),
                                Component.empty(),
                                text(
                                    "예상 회복량",
                                    NamedTextColor.GRAY
                                ),
                                text(
                                    "+${heal.toInt()} HP",
                                    NamedTextColor.GREEN
                                ),
                                Component.empty(),
                                text(
                                    if (all)
                                        "클릭하여 모두 사용합니다."
                                    else
                                        "클릭하여 1개를 사용합니다.",
                                    NamedTextColor.YELLOW
                                )
                            )
                        )
                    }
                }
            }

            inv.setItem(
                10,
                repairItem(
                    Material.COPPER_INGOT,
                    1,
                    "구리 주괴로 수리",
                    ingots,
                    minOf(
                        COPPER_INGOT_HEAL,
                        missingHp
                    ),
                    false
                )
            )

            inv.setItem(
                12,
                repairItem(
                    Material.COPPER_INGOT,
                    ingots.coerceAtLeast(1),
                    "구리 주괴 모두 사용",
                    ingots,
                    minOf(
                        ingots * COPPER_INGOT_HEAL,
                        missingHp
                    ),
                    true
                )
            )

            inv.setItem(
                14,
                repairItem(
                    Material.COPPER_BLOCK,
                    1,
                    "구리 블록으로 수리",
                    blocks,
                    minOf(
                        COPPER_BLOCK_HEAL,
                        missingHp
                    ),
                    false
                )
            )

            inv.setItem(
                16,
                repairItem(
                    Material.COPPER_BLOCK,
                    blocks.coerceAtLeast(1),
                    "구리 블록 모두 사용",
                    blocks,
                    minOf(
                        blocks * COPPER_BLOCK_HEAL,
                        missingHp
                    ),
                    true
                )
            )
        }

        inv.setItem(
            26,
            ItemStack(Material.BARRIER).apply {
                editMeta {
                    it.displayName(
                        text("닫기", NamedTextColor.RED)
                    )
                }
            }
        )
    }

    fun repairBase(
        player: Player,
        base: BaseEntity,
        material: Material,
        useAll: Boolean
    ): Component {
        val maxHp = baseHp[base.info.level]

        if (base.info.hp >= maxHp) {
            return Component.text(
                "이미 최대 체력입니다.",
                NamedTextColor.RED
            )
        }

        val healPerItem = when (material) {
            Material.COPPER_INGOT -> COPPER_INGOT_HEAL
            Material.COPPER_BLOCK -> COPPER_BLOCK_HEAL
            else -> return Component.text(
                "잘못된 재료입니다.",
                NamedTextColor.RED
            )
        }

        val amount = if (useAll) {
            player.inventory
                .all(material)
                .values
                .sumOf { it.amount }
        } else {
            1
        }

        if (amount <= 0) {
            return Component.text(
                "재료가 부족합니다.",
                NamedTextColor.RED
            )
        }

        val missingHp = maxHp - base.info.hp

        // 실제 필요한 아이템 개수
        val neededItems = kotlin.math.ceil(
            missingHp / healPerItem
        ).toInt()

        // 실제 사용할 개수
        val usedItems = minOf(amount, neededItems)

        player.inventory.removeItem(
            ItemStack(material, usedItems)
        )

        base.info.hp = minOf(
            maxHp,
            base.info.hp + usedItems * healPerItem
        )

        save()

        return Component.text(
            "기지를 ${
                (usedItems * healPerItem).toInt()
            }만큼 수리했습니다.",
            NamedTextColor.GREEN
        )
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        val holder = event.view.topInventory.holder ?: return


        // 기지 목록
        if (holder is BaseListHolder) {
            event.isCancelled = true

            if (event.clickedInventory != event.view.topInventory) return

            val item = event.currentItem ?: return

            val id = item.itemMeta.persistentDataContainer.get(
                makeKey("baseId"),
                PersistentDataType.STRING
            ) ?: return

            val base = bases[UUID.fromString(id)] ?: return

            openBaseMenu(player, base)
            return
        }


        // 기지 메뉴
        if (holder is BaseMenuHolder) {
            event.isCancelled = true

            if (event.clickedInventory != event.view.topInventory) return

            val base = holder.base

            when (event.rawSlot) {

                28 -> {
                    openStorage(player, base)
                }


                30 -> {
                    rename(player, base)

                    updateBaseMenu(base)
                    player.closeInventory()
                }


                33 -> {
                    player.sendMessage(setSpawnLocation(base, player))

                    player.closeInventory()
                }


                13 -> {
                    val error = upgrade(base, player)

                    if (error != null) {
                        player.sendMessage(error)
                        return
                    }

                    player.sendMessage("기지를 업그레이드했습니다.")

                    updateBaseMenu(base)
                }


                32 -> {
                    val revived = mutableListOf<String>()

                    for (banned in Bukkit.getServer().bannedPlayers) {

                        if (banned.getTeam() != player.getTeam()) continue

                        bases.values.forEach {
                            it.info.spawnPlayers.remove(banned.uniqueId)
                        }

                        base.info.spawnPlayers += banned.uniqueId

                        Bukkit.getBanList(BanListType.PROFILE)
                            .getBanEntry(banned.playerProfile)
                            ?.remove()

                        banned.name?.let(revived::add)
                    }


                    val text =
                        when {
                            revived.isEmpty() ->
                                "부활시킬 팀원이 없습니다."

                            revived.size <= 3 ->
                                "${revived.joinToString(", ")}을(를) 부활시켰습니다."

                            else ->
                                "${revived.take(3).joinToString(", ")} 외 ${revived.size - 3}명을 부활시켰습니다."
                        }

                    player.sendMessage(text)
                    player.closeInventory()
                }


                34 -> {
                    player.sendMessage("스폰 설정이 완료되었습니다.")

                    bases.values.forEach {
                        it.info.spawnPlayers.remove(player.uniqueId)
                    }

                    base.info.spawnPlayers += player.uniqueId

                    save()

                    player.closeInventory()
                }


                31 -> {
                    player.teleport(base.info.spawnLocation)
                    player.closeInventory()
                }

                29 -> {
                    player.closeInventory()

                    openRepairMenu(player, base)
                }


                44 -> {
                    player.closeInventory()
                }
            }

            return
        }

        if (holder is RepairMenuHolder) {
            event.isCancelled = true

            if (event.clickedInventory != event.view.topInventory) return

            val base = holder.base

            val message = when (event.rawSlot) {
                10 -> repairBase(
                    player,
                    base,
                    Material.COPPER_INGOT,
                    false
                )

                12 -> repairBase(
                    player,
                    base,
                    Material.COPPER_INGOT,
                    true
                )

                14 -> repairBase(
                    player,
                    base,
                    Material.COPPER_BLOCK,
                    false
                )

                16 -> repairBase(
                    player,
                    base,
                    Material.COPPER_BLOCK,
                    true
                )

                26 -> {
                    player.closeInventory()
                    return
                }

                else -> return
            }

            player.sendMessage(message)

            updateRepairMenu(
                event.view.topInventory,
                player,
                base
            )

            updateBaseMenu(base)
        }


        // 창고
        if (holder is StorageHolder) {
            val base = holder.base

            bukkitRunnable {
                base.info.inventoryEncoded =
                    event.view.topInventory.toStringList()

                save()
            }.runTask(plugin)
        }
    }

    fun openStorage(player: Player, base: BaseEntity) {
        if (base.info.inventoryEncoded.isEmpty()) {
            player.sendMessage("기지 인벤토리를 사용할 수 없습니다.")
            return
        }

        val inventory = base.storageInventory ?: Bukkit.createInventory(
            StorageHolder(base),
            baseStorageSize[base.info.level],
            Component.text("창고")
        ).apply {
            val old = base.info.inventoryEncoded.toInventory()

            for (i in 0 until minOf(size, old.size)) {
                setItem(i, old.getItem(i))
            }
        }.also {
            base.storageInventory = it
        }

        player.openInventory(inventory)
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val inventory = event.inventory
        val holder = inventory.holder ?: return

        when (holder) {

            is StorageHolder -> {
                val base = holder.base

                val storage = base.storageInventory

                if (storage === inventory) {
                    base.info.inventoryEncoded = inventory.toStringList()
                    save()

                    // 마지막 사용자가 닫았으면 메모리 제거
                    if (inventory.viewers.isEmpty()) {
                        base.storageInventory = null
                    }
                }
            }
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        cleanupPlayer(event.player)
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val inventory = event.view.topInventory
        val holder = inventory.holder ?: return


        when (holder) {

            is StorageHolder -> {
                val base = holder.base

                bukkitRunnable {
                    base.info.inventoryEncoded = inventory.toStringList()
                    save()
                }.runTask(plugin)

                return
            }


            is BaseMenuHolder,
            is BaseListHolder -> {
                if (event.rawSlots.any { it < event.view.topInventory.size }) {
                    event.isCancelled = true
                }
            }
        }
    }

    fun getSpawnBase(player: Player): BaseEntity? =
        bases.values.firstOrNull {
            player.uniqueId in it.info.spawnPlayers
        }

    fun setSpawnLocation(base: BaseEntity, player: Player): Component {
        if (!player.location.isWithinXZ(base.location, 100.0)) {
            return Component.text("기지 반경 100블록 안에서만 스폰 위치를 설정할 수 있습니다.")
        }

        base.info.spawnLocation = player.location.clone()

        save()

        return Component.text("스폰 위치가 설정되었습니다.")
    }

    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        val base = getSpawnBase(player)

        if (base == null) {
            cleanupPlayer(player)

            pendingRespawn += player.uniqueId
            save()

            val message = "스폰포인트가 없습니다. 기지에서 팀원에게 부활을 요청하세요."
            player.kick(Component.text(message))

            player.ban<BanEntry<PlayerProfile>>(
                message,
                null as Instant?,
                "Console"
            )
        } else {
            event.respawnLocation = base.info.spawnLocation
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        if (!pendingRespawn.remove(player.uniqueId))
            return

        save()

        val base = getSpawnBase(player) ?: return

        player.teleport(base.info.spawnLocation)
        bukkitRunnable {
            player.teleport(base.info.spawnLocation)
        }.runTask(plugin)
    }

    fun cleanupPlayer(player: Player) {
        val uuid = player.uniqueId

        val top = player.openInventory.topInventory
        val holder = top.holder

        if (holder is StorageHolder) {
            val base = holder.base

            if (top.viewers.size <= 1) {
                base.storageInventory = null
            }
        }

        renaming.remove(uuid)
        renameTasks.remove(uuid)?.cancel()

        if (isCombating(uuid)) {
            Bukkit.broadcast(
                Component.text(
                    "[알림] ${player.name}이(가) 추하게 싸우다가 게임을 나가서 죽었습니다"
                )
            )
            player.health = 0.0
        }

        remove(player)
        removeMarker(player)
        hasChatted.remove(uuid)
    }

    @EventHandler
    fun onBaseDamage(event: EntityDamageByEntityEvent) {
        val interaction = event.entity as? Interaction ?: return
        val player = event.damager as? Player ?: return

        val base = bases.values.firstOrNull {
            it.interaction?.uniqueId == interaction.uniqueId
        } ?: return

        event.isCancelled = true

        if (base.info.baseTeam == player.getTeam()?.name) {
            return
        }

        base.info.hp -= player.getAttribute(Attribute.ATTACK_DAMAGE)?.value ?: return

        interaction.world.playSound(
            interaction.location,
            Sound.ENTITY_PLAYER_ATTACK_CRIT,
            1f,
            1f
        )

        interaction.world.spawnParticle(
            Particle.CRIT,
            interaction.location.add(0.5, 0.5, 0.5),
            10,
            0.2,
            0.2,
            0.2,
            0.0
        )

        if (base.info.hp <= 0.0) {
            removeBase(base, player.name)
        } else {
            save()
        }
    }

    fun saveStorage() {
        bases.values.forEach { base ->
            base.storageInventory?.let { inventory ->
                base.info.inventoryEncoded = inventory.toStringList()
            }
        }

        save()
    }
}