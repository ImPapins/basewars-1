package kr.kro.impapins.minecraft.baseWars

import io.papermc.paper.event.player.AsyncChatEvent
import kr.kro.impapins.minecraft.baseWars.TeamManager.getTeam
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID

object TeamChatListener : Listener {
    val hasChatted = mutableSetOf<UUID>()

    @EventHandler
    fun onChat(event: AsyncChatEvent) {
        val player = event.player

        if (player.uniqueId in BaseManager.renaming) return
        val team = player.getTeam() ?: return

        val uuid = player.uniqueId
        if (uuid !in hasChatted) {
            player.sendMessage("[알림] 현재 팀 채팅을 사용중입니다.\n[알림] 전체 채팅을 사용하려면 /ac <메시지>를 사용하세요.")
            hasChatted.add(uuid)
        }

        event.viewers().removeIf { viewer ->
            viewer !is Player || viewer.getTeam() != team
        }

        event.renderer { source, _, message, _ ->
            Component.text()
                .append(Component.text("[팀] "))
                .append(source.displayName())
                .append(Component.text(": "))
                .append(message)
                .build()
                .color(team.color)
        }
    }

    // @EventHandler
    // fun onQuit(event: PlayerQuitEvent) {
    //     hasChatted.remove(event.player.uniqueId)
    // }
}