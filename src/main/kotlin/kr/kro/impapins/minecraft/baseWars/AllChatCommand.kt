package kr.kro.impapins.minecraft.baseWars

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player

object AllChatCommand : BasicCommand {
    override fun execute(
        source: CommandSourceStack,
        args: Array<String>
    ) {
        val player = source.sender as? Player ?: return

        if (args.isEmpty()) {
            source.sender.sendMessage("사용법: /ac <메시지>")
            return
        }

        val message = args.joinToString(" ")

        Bukkit.broadcast(
            Component.text()
                .append(Component.text("[전체] "))
                .append(player.teamDisplayName())
                .append(Component.text(": "))
                .append(Component.text(message))
                .build()
        )
    }
}