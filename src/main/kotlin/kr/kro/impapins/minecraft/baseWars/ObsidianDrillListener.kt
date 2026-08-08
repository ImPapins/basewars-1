package kr.kro.impapins.minecraft.baseWars

import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockDamageEvent

object ObsidianPickaxeListener : Listener {
    val obsidianPickaxeMineable = listOf(
        Material.OBSIDIAN,
        Material.CRYING_OBSIDIAN,
        Material.ENDER_CHEST
    )

    @EventHandler
    fun onBreakBlock(event: BlockDamageEvent) {
        val player = event.player
        val item = player.inventory.itemInMainHand

        if (!item.hasTag(makeKey("obsidianPickaxe"))) return

        if (event.block.type in obsidianPickaxeMineable) {
            event.instaBreak = true
            
        }
    }
}