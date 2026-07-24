package kr.kro.impapins.minecraft.baseWars

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

class BaseListHolder : InventoryHolder {
    override fun getInventory(): Inventory {
        throw UnsupportedOperationException()
    }
}

class BaseMenuHolder(
    val base: BaseEntity
) : InventoryHolder {
    override fun getInventory(): Inventory {
        throw UnsupportedOperationException()
    }
}

class StorageHolder(
    val base: BaseEntity
) : InventoryHolder {
    override fun getInventory(): Inventory {
        throw UnsupportedOperationException()
    }
}

class RepairMenuHolder(
    val base: BaseEntity
) : InventoryHolder {
    override fun getInventory(): Inventory {
        throw UnsupportedOperationException()
    }
}