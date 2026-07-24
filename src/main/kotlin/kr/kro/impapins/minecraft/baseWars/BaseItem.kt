package kr.kro.impapins.minecraft.baseWars

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

object BaseItem {
    fun create(): ItemStack {
        val item = ItemStack(Material.NETHER_STAR)
        val meta = item.itemMeta

        meta.displayName(Component.text("기지 설치"))

        item.itemMeta = meta

        item.setTag(makeKey("baseItem"))

        return item
    }
}