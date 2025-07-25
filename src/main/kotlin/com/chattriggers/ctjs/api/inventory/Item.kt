package com.chattriggers.ctjs.api.inventory

import com.chattriggers.ctjs.api.CTWrapper
import com.chattriggers.ctjs.api.client.Client
import com.chattriggers.ctjs.api.client.Player
import com.chattriggers.ctjs.api.entity.Entity
import com.chattriggers.ctjs.api.inventory.nbt.NBTTagCompound
import com.chattriggers.ctjs.api.message.TextComponent
import com.chattriggers.ctjs.api.render.Renderer
import com.chattriggers.ctjs.api.world.World
import com.chattriggers.ctjs.api.world.block.Block
import com.chattriggers.ctjs.api.world.block.BlockPos
import com.chattriggers.ctjs.internal.Skippable
import com.chattriggers.ctjs.internal.TooltipOverridable
import com.chattriggers.ctjs.MCNbtCompound
import com.chattriggers.ctjs.internal.utils.asMixin
import net.minecraft.block.pattern.CachedBlockPosition
import net.minecraft.client.render.DiffuseLighting
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.item.ItemRenderState
import net.minecraft.component.DataComponentTypes
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.item.Item.TooltipContext
import net.minecraft.item.ItemStack
import net.minecraft.item.ItemDisplayContext
import net.minecraft.item.tooltip.TooltipType
import net.minecraft.util.crash.CrashException
import net.minecraft.util.crash.CrashReport
import kotlin.jvm.optionals.getOrNull

class Item(override val mcValue: ItemStack) : CTWrapper<ItemStack> {
    val type: ItemType = ItemType(mcValue.item)

    init {
        require(!mcValue.isEmpty) {
            "Can not wrap empty ItemStack as an Item"
        }
    }

    constructor(type: ItemType) : this(type.toMC().defaultStack)

    fun getHolder(): Entity? = mcValue.holder?.let(Entity::fromMC)

    fun getStackSize(): Int = mcValue.count

    fun setStackSize(size: Int) = apply {
        mcValue.count = size
    }

    fun getEnchantments() = EnchantmentHelper.getEnchantments(mcValue).enchantments.associate {
        it.key.getOrNull() to EnchantmentHelper.getLevel(it, mcValue)
    }

    fun isEnchantable() = mcValue.isEnchantable

    fun isEnchanted() = mcValue.hasEnchantments()

    fun canPlaceOn(pos: BlockPos) =
        mcValue.canPlaceOn(CachedBlockPosition(World.toMC(), pos.toMC(), false))

    fun canPlaceOn(block: Block) = canPlaceOn(block.pos)

    fun canHarvest(pos: BlockPos) =
        mcValue.canBreak(CachedBlockPosition(World.toMC(), pos.toMC(), false))

    fun canHarvest(block: Block) = canHarvest(block.pos)

    fun getDurability() = getMaxDamage() - getDamage()

    fun getMaxDamage() = mcValue.maxDamage

    fun getDamage() = mcValue.damage

    fun isDamageable() = mcValue.isDamageable

    fun getName(): String = TextComponent(mcValue.name).formattedText

    fun setName(name: TextComponent?) = apply {
        mcValue.set(DataComponentTypes.CUSTOM_NAME, name)
    }

    fun resetName() {
        setName(null)
    }

    @JvmOverloads
    fun getLore(advanced: Boolean = false): List<TextComponent> {
        mcValue.asMixin<Skippable>().ctjs_setShouldSkip(true)
        val tooltip = mcValue.getTooltip(
            TooltipContext.DEFAULT,
            Player.toMC(),
            if (advanced) TooltipType.ADVANCED else TooltipType.BASIC,
        ).mapTo(mutableListOf()) { TextComponent(it) }

        mcValue.asMixin<Skippable>().ctjs_setShouldSkip(false)

        return tooltip
    }

    fun setLore(lore: List<TextComponent>) {
        mcValue.asMixin<TooltipOverridable>().apply {
            ctjs_setTooltip(lore)
            ctjs_setShouldOverrideTooltip(true)
        }
    }

    fun resetLore() {
        mcValue.asMixin<TooltipOverridable>().ctjs_setShouldOverrideTooltip(false)
    }

    // TODO: make a component wrapper?
    fun getNBT() = mcValue.components

    /**
     * Renders the item icon to the client's overlay, with customizable overlay information.
     *
     * @param x the x location
     * @param y the y location
     * @param scale the scale
     * @param z the z level to draw the item at
     */
    @JvmOverloads
    fun draw(x: Float = 0f, y: Float = 0f, scale: Float = 1f, z: Float = 200f) {
        val itemRenderer = Client.getMinecraft().itemRenderer
        val itemRenderState = ItemRenderState()

        Renderer.pushMatrix()
            .scale(scale, scale, 1f)
            .translate(x / scale, y / scale, z)

        // The item draw method moved to DrawContext in 1.20, which we don't have access
        // to here, so its drawItem method has been copy-pasted here instead
        if (mcValue.isEmpty)
            return
        Client.getMinecraft().itemModelManager.clearAndUpdate(itemRenderState, mcValue, ItemDisplayContext.GUI, World.toMC(), null, 0)
        Renderer.pushMatrix()
        Renderer.translate(x + 8, y + 8, 150 + z)
        try {
            val vertexConsumers = Client.getMinecraft().bufferBuilders.entityVertexConsumers
            Renderer.scale(16.0f, -16.0f, 16.0f)
            if (!itemRenderState.isSideLit)
                vertexConsumers.draw()
                DiffuseLighting.disableGuiDepthLighting()

            itemRenderState.render(Renderer.matrixStack.toMC(), vertexConsumers, 15728880, OverlayTexture.DEFAULT_UV)

            Renderer.disableDepth()
            vertexConsumers.draw()
            Renderer.enableDepth()

            if (!itemRenderState.isSideLit) {
                DiffuseLighting.enableGuiDepthLighting()
            }
        } catch (e: Throwable) {
            val crashReport = CrashReport.create(e, "Rendering item")
            val crashReportSection = crashReport.addElement("Item being rendered")
            crashReportSection.add("Item Type") { mcValue.item.toString() }
            crashReportSection.add("Item Damage") { mcValue.damage.toString() }
            crashReportSection.add("Item Components") { mcValue.components.toString() }
            crashReportSection.add("Item Foil") { mcValue.hasGlint().toString() }
            throw CrashException(crashReport)
        } finally {
            Renderer.popMatrix()
            Renderer.popMatrix()
        }
    }

    override fun toString(): String = "Item{name=${getName()}, type=${type.getRegistryName()}, size=${getStackSize()}}"

    companion object {
        @JvmStatic
        fun fromMC(mcValue: ItemStack): Item? {
            return if (mcValue.isEmpty) {
                null
            } else {
                Item(mcValue)
            }
        }
    }
}
