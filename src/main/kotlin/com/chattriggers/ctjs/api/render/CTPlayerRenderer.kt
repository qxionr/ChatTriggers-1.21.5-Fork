package com.chattriggers.ctjs.api.render

import net.minecraft.client.network.AbstractClientPlayerEntity
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.PlayerEntityRenderer
import net.minecraft.client.render.entity.feature.*
import net.minecraft.client.render.entity.model.ArmorEntityModel
import net.minecraft.client.render.entity.model.EntityModelLayers
import net.minecraft.client.render.entity.model.LoadedEntityModels
import net.minecraft.client.render.entity.state.PlayerEntityRenderState
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text

internal class CTPlayerRenderer(
    private val ctx: EntityRendererFactory.Context,
    private val slim: Boolean,
) : PlayerEntityRenderer(ctx, slim) {
    var showArmor = true
        set(value) {
            field = value
            reset()
        }
    var showHeldItem = true
        set(value) {
            field = value
            reset()
        }
    var showArrows = true
        set(value) {
            field = value
            reset()
        }
    var showCape = true
        set(value) {
            field = value
            reset()
        }
    var showElytra = true
        set(value) {
            field = value
            reset()
        }
    var showParrot = true
        set(value) {
            field = value
            reset()
        }
    var showStingers = true
        set(value) {
            field = value
            reset()
        }
    var showNametag = true
        set(value) {
            field = value
            reset()
        }

    fun setOptions(
        showNametag: Boolean = true,
        showArmor: Boolean = true,
        showCape: Boolean = true,
        showHeldItem: Boolean = true,
        showArrows: Boolean = true,
        showElytra: Boolean = true,
        showParrot: Boolean = true,
        showStingers: Boolean = true,
    ) {
        this.showNametag = showNametag
        this.showArmor = showArmor
        this.showCape = showCape
        this.showHeldItem = showHeldItem
        this.showArrows = showArrows
        this.showElytra = showElytra
        this.showParrot = showParrot
        this.showStingers = showStingers

        reset()
    }


    override fun renderLabelIfPresent(
        playerEntityRenderState: PlayerEntityRenderState,
        text: Text,
        matrixStack: MatrixStack,
        vertexConsumerProvider: VertexConsumerProvider,
        i: Int
    ) {
        if (showNametag)
            super.renderLabelIfPresent(playerEntityRenderState, text, matrixStack, vertexConsumerProvider, i)
    }

    private fun reset() {
        features.clear()

        val entityModels = ctx.modelManager.entityModelsSupplier.get()

        if (showArmor) {
            addFeature(
                ArmorFeatureRenderer(
                    this,
                    ArmorEntityModel(ctx.getPart(if (slim) EntityModelLayers.PLAYER_SLIM_INNER_ARMOR else EntityModelLayers.PLAYER_INNER_ARMOR)),
                    ArmorEntityModel(ctx.getPart(if (slim) EntityModelLayers.PLAYER_SLIM_OUTER_ARMOR else EntityModelLayers.PLAYER_OUTER_ARMOR)),
                    ctx.equipmentRenderer
                )
            )
        }
        if (showHeldItem)
            addFeature(PlayerHeldItemFeatureRenderer(this))
        if (showArrows)
            addFeature(StuckArrowsFeatureRenderer(this, ctx))
        addFeature(Deadmau5FeatureRenderer(this, entityModels))
        if (showCape)
            addFeature(CapeFeatureRenderer(this, entityModels, ctx.equipmentModelLoader))
        if (showArmor)
            addFeature(HeadFeatureRenderer(this, entityModels))
        if (showElytra)
            addFeature(ElytraFeatureRenderer(this, entityModels, ctx.equipmentRenderer))
        if (showParrot)
            addFeature(ShoulderParrotFeatureRenderer(this, entityModels))
        addFeature(TridentRiptideFeatureRenderer(this, entityModels))
        if (showStingers)
            addFeature(StuckStingersFeatureRenderer(this, ctx))
    }
}
