package kr.kro.impapins.minecraft.baseWars

import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.ItemFrame
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.EntitiesLoadEvent
import org.bukkit.inventory.ItemStack

object ElytraListener : Listener {
    @EventHandler
    fun onEntitiesLoad(event: EntitiesLoadEvent) {
        if (event.world.environment != World.Environment.THE_END) {
            return
        }

        for (entity in event.entities) {
            val frame = entity as? ItemFrame ?: continue

            if (frame.item.type == Material.ELYTRA) {
                frame.setItem(ItemStack(Material.DIAMOND))
            }
        }
    }
}