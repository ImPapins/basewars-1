package kr.kro.impapins.minecraft.baseWars

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.entity.Player

object BaseCommand : BasicCommand {
    override fun execute(
        source: CommandSourceStack,
        args: Array<String>
    ) {
        val sender = source.sender

        if (sender !is Player) return

        if (!sender.isOp) {
            sender.sendMessage("OP가 없어서 사용할 수 없습니다.")
            return
        }

        sender.inventory.addItem(
            BaseItem.create()
        )
    }
}