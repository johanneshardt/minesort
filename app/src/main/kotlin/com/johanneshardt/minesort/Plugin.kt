package com.johanneshardt.minesort

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.enchantments.EnchantmentTarget
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.inventory.InventoryType.SlotType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import org.bukkit.plugin.java.JavaPlugin

class Plugin : JavaPlugin(), Listener {
    override fun onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this)
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked
        /* many interactables (for example anvils) count as inventories themselves,
           but also allow access to the player inventory in the bottom half of the ui. By checking
           .clickedInventory (instead of event.inventory) we still allow for sorting in that case! */
        val inventory = event.clickedInventory

        if (event.click == ClickType.DOUBLE_CLICK && inventory != null) {
            val inventoryName = inventory.type.defaultTitle()

            when (inventory.type) {
                InventoryType.CHEST,
                InventoryType.ENDER_CHEST,
                InventoryType.SHULKER_BOX,
                InventoryType.DISPENSER,
                InventoryType.DROPPER,
                InventoryType.HOPPER,
                InventoryType.BARREL -> {
                    inventory.sort()
                    player.sendMessage(inventoryName.append(Component.text(" was sorted!")).color(NamedTextColor.GREEN))
                }

                InventoryType.PLAYER -> {
                    // Ignore clicks in crafting/armor/right hand containers
                    if (event.slotType == SlotType.CONTAINER) {
                        inventory.sort()
                        player.sendMessage(Component.text("Your inventory was sorted!", NamedTextColor.GREEN))
                    }
                }

                else -> {}
            }
        }
    }
}


fun Inventory.sort() {
    // storageContents for the player inventory includes the hotbar, which I don't want to sort
    val (hotbarItems, storageItems) = when (this.type) {
        InventoryType.PLAYER -> Pair(this.storageContents.take(9), this.storageContents.drop(9))
        else -> Pair(listOf(), this.storageContents.toList())
    }
    val containerSize = storageItems.size
    val combined = combineStacks(storageItems.filterNotNull().map { it.clone() })
    val sorted = combined.sortedWith(itemStackOrder)

    // we have to set storageContents to a list of the correct size, so we pad it with nulls
    this.storageContents = hotbarItems.toTypedArray() + sorted + arrayOfNulls(containerSize - sorted.size)
}

/**
 * Combines stacks that are truly similar (using [ItemStack.isSimilar]) and not full.
 * Returns a list of ItemStacks with merged amounts.
 *
 * Since [combineStacks] modifies [ItemStack]s, be sure to pass in cloned instances!
 */
fun combineStacks(stacks: List<ItemStack>): List<ItemStack> {
    val combined = mutableListOf<ItemStack>()
    for (stack in stacks) {
        // Try merging with any existing stack in combined that is similar and has space left
        for (existing in combined) {
            if (existing.isSimilar(stack) && existing.amount < existing.type.maxStackSize) {
                val spaceLeft = existing.type.maxStackSize - existing.amount
                val toMerge = minOf(spaceLeft, stack.amount)
                existing.amount += toMerge
                stack.amount -= toMerge
                if (stack.amount <= 0) break
            }
        }
        // If there is any remainder, add it as a new stack.
        if (stack.amount > 0) {
            combined.add(stack)
        }
    }
    return combined
}

// getItemMeta() is only null for Material.AIR as far as I know, but we handle it just in case.
val itemStackOrder = compareByDescending<ItemStack> { it.type.isSolid }
    .thenByDescending { it.type.isBlock } // then blocks
    .thenBy { EnchantmentTarget.ALL.includes(it) } // check if item is enchantable
    .thenBy { it.type == Material.ENCHANTED_BOOK } // enchanted books at the end
    .thenByDescending { it.itemMeta?.enchants?.size }  // sort by number of enchantments
    .thenBy(nullsLast<String>()) {
        (it.itemMeta as? EnchantmentStorageMeta)?.storedEnchants?.entries?.firstOrNull()?.let { (enchantment, level) ->
            PlainTextComponentSerializer.plainText().serialize(enchantment.displayName(level))
        }
    } // sort books by enchant name
    .thenBy { it.type.isEdible } // food last
    .thenByDescending { it.type.maxStackSize } // largest max stacks first
    .thenBy { PlainTextComponentSerializer.plainText().serialize(it.effectiveName()) } // sort by display name
    .thenByDescending { it.amount } // same items with different amounts