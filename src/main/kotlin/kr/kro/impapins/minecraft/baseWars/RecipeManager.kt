@file:Suppress("UnstableApiUsage")
package kr.kro.impapins.minecraft.baseWars

import com.destroystokyo.paper.profile.ProfileProperty
import io.papermc.paper.datacomponent.DataComponentTypes
import kr.kro.impapins.minecraft.baseWars.ObsidianDrillListener.obsidianDrillMineable
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.meta.SkullMeta
import java.util.*

object RecipeManager : Listener {
    fun init() {
        addGoldenHead()
        addBase()
        addBaseCompass()
        addObsidianDrill()
    }

    private fun addGoldenHead() {
        val head = ItemStack(Material.PLAYER_HEAD)
        val meta = head.itemMeta as SkullMeta

        val profile = Bukkit.createProfile(UUID.randomUUID())

        profile.setProperty(
            ProfileProperty(
                "textures",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzU2NmYyZjg3Y2NkYzU4ZjY3ODI5ZDQ2Njc3ZjIyYmUyNWRmMTdjMDFlMmQ5NDRlZmE3ZWJlNjI2MjI5ZjgyZSJ9fX0="
            )
        )

        meta.playerProfile = profile

        meta.displayName(
            Component.text(
                "황금 머리",
                NamedTextColor.GOLD,
                TextDecoration.BOLD
            )
                .decoration(TextDecoration.ITALIC, false)
        )

        head.itemMeta = meta

        head.setTag(makeKey("playerHead"))
        head.setTag(makeKey("goldenHead"))


        val recipe = ShapedRecipe(
            makeKey("goldenHeadRecipe"),
            head
        )

        recipe.shape(
            "AAA",
            "ABA",
            "AAA"
        )

        recipe.setIngredient('A', Material.GOLD_INGOT)
        recipe.setIngredient('B', Material.PLAYER_HEAD)

        Bukkit.addRecipe(recipe)
    }

    private fun addBase() {
        val result = BaseItem.create()

        val recipe = ShapedRecipe(
            makeKey("baseRecipe"),
            result
        )

        recipe.shape(
            "DND",
            "NBN",
            "DND"
        )

        recipe.setIngredient('D', Material.DIAMOND_BLOCK)
        recipe.setIngredient('N', Material.NETHERITE_INGOT)
        recipe.setIngredient('B', Material.BEACON)

        Bukkit.addRecipe(recipe)
    }

    private fun addBaseCompass() {
        val compass = ItemStack(Material.RECOVERY_COMPASS)

        compass.editMeta {
            it.displayName(
                Component.text(
                    "기지 나침반",
                    NamedTextColor.AQUA,
                    TextDecoration.BOLD
                ).decoration(TextDecoration.ITALIC, false)
            )

            it.lore(
                listOf(
                    Component.text(
                        "가장 가까운 적 기지를 탐지합니다.",
                        NamedTextColor.GRAY
                    ).decoration(TextDecoration.ITALIC, false)
                )
            )
        }

        compass.setTag(makeKey("baseCompass"))

        val recipe = ShapedRecipe(
            makeKey("baseCompassRecipe"),
            compass
        )

        recipe.shape(
            "SRS",
            "RCR",
            "SRS"
        )

        recipe.setIngredient('S', Material.NETHERITE_SCRAP)
        recipe.setIngredient('R', Material.REDSTONE_BLOCK)
        recipe.setIngredient('C', Material.RECOVERY_COMPASS)

        Bukkit.addRecipe(recipe)
    }

    private fun addObsidianDrill() {
        val drill = ItemStack(Material.AMETHYST_SHARD)

        drill.editMeta {
            it.displayName(
                Component.text(
                    "흑요석 드릴",
                    NamedTextColor.DARK_PURPLE,
                    TextDecoration.BOLD
                ).decoration(TextDecoration.ITALIC, false)
            )

            it.lore(
                buildList {
                    add(
                        Component.text(
                            "아래 블록을 즉시 파괴할 수 있습니다. 대신, 다른 블록은 파괴할 수 없습니다.",
                            NamedTextColor.GRAY
                        ).decoration(TextDecoration.ITALIC, false)
                    )

                    add(Component.empty())

                    for (block in obsidianDrillMineable) {
                        add(
                            Component.text(
                                "- "
                            ).append(
                                Component.translatable(block.translationKey())
                                    .decoration(TextDecoration.ITALIC, false)
                                    .color(NamedTextColor.LIGHT_PURPLE)
                            )
                        )
                    }

                    add(Component.empty())

                    add(
                        Component.text(
                            "인벤토리를 열고 용암 양동이를 든 상태로 클릭해 연료를 충전할 수 있습니다.",
                            NamedTextColor.GRAY
                        ).decoration(TextDecoration.ITALIC, false)
                    )
                }
            )

            it.setMaxStackSize(1)
        }

        drill.setData(DataComponentTypes.MAX_DAMAGE, 100)
        drill.setData(DataComponentTypes.DAMAGE, 0)

        drill.setTag(makeKey("obsidianDrill"))

        val recipe = ShapedRecipe(
            makeKey("obsidianDrillRecipe"),
            drill
        )

        recipe.shape(
            "COC",
            "OSO",
            "COC"
        )

        recipe.setIngredient('O', Material.OBSIDIAN)
        recipe.setIngredient('S', Material.AMETHYST_SHARD)
        recipe.setIngredient('C', Material.CRYING_OBSIDIAN)

        Bukkit.addRecipe(recipe)
    }

    val customRecipes = listOf(
        makeKey("goldenHeadRecipe"),
        makeKey("baseCompassRecipe"),
        makeKey("baseRecipe"),
        makeKey("obsidianDrillRecipe")
    )

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        customRecipes.forEach(event.player::discoverRecipe)
    }
}