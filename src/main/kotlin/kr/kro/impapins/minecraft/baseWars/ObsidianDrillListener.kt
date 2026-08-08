@file:Suppress("UnstableApiUsage")
package kr.kro.impapins.minecraft.baseWars

import io.papermc.paper.datacomponent.DataComponentTypes
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockDamageEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.ItemStack

object ObsidianDrillListener : Listener {
    val obsidianDrillMineable = listOf(
        Material.OBSIDIAN,
        Material.CRYING_OBSIDIAN,
        Material.ENDER_CHEST
    )

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        if (event.view.topInventory.type == InventoryType.PLAYER) return

        val cursor = event.cursor
        val clicked = event.currentItem ?: return

        if (!clicked.hasTag(makeKey("obsidianDrill"))) return
        if (cursor.type != Material.LAVA_BUCKET) return
        if ((clicked.getData(DataComponentTypes.DAMAGE) ?: 0) == 0) return

        event.isCancelled = true

        player.playSound(
            player.location,
            Sound.ENTITY_GENERIC_BURN,
            1f,
            1f
        )

        clicked.setData(DataComponentTypes.DAMAGE, 0)

        player.setItemOnCursor(ItemStack(Material.BUCKET))
    }


    @EventHandler
    fun onDamageBlock(event: BlockDamageEvent) {
        val player = event.player
        val item = player.inventory.itemInMainHand

        if (!item.hasTag(makeKey("obsidianDrill"))) return

        val damage = item.getData(DataComponentTypes.DAMAGE) ?: 0
        val maxDamage = item.getData(DataComponentTypes.MAX_DAMAGE) ?: return

        if (damage == maxDamage - 1) {
            event.isCancelled = true

            player.sendMessage("흑요석 드릴의 연료가 부족합니다.")
            player.playSound(
                player.location,
                Sound.BLOCK_AMETHYST_BLOCK_PLACE,
                2f,
                0.5f
            )
        }

        if (event.block.type in obsidianDrillMineable) {
            event.instaBreak = true
        } else {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onBreakBlock(event: BlockBreakEvent) {
        val player = event.player
        val item = player.inventory.itemInMainHand

        if (!item.hasTag(makeKey("obsidianDrill"))) return

        val damage = item.getData(DataComponentTypes.DAMAGE) ?: 0

        item.setData(DataComponentTypes.DAMAGE, damage + 1)

        event.isDropItems = false

        val location = event.block.location.add(0.5, 0.5, 0.5)

        location.world.dropItemNaturally(
            location,
            ItemStack(event.block.type)
        )

        event.block.world.playSound(
            location,
            Sound.BLOCK_AMETHYST_BLOCK_BREAK,
            1f,
            1f
        )

        location.world.spawnParticle(
            Particle.PORTAL,
            location,
            1000,
            0.5,
            0.5,
            0.5,
            0.0
        )
    }
}