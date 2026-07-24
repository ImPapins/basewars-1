package kr.kro.impapins.minecraft.baseWars

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.entity.Player

object BaseMenuCommand : BasicCommand {
    override fun execute(
        source: CommandSourceStack,
        args: Array<String>
    ) {
        val sender = source.sender

        if (sender !is Player) return

        BaseManager.openBaseList(sender)
    }
}