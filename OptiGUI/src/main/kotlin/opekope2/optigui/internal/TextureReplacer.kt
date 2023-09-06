package opekope2.optigui.internal

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.hit.HitResult
import net.minecraft.world.World
import opekope2.lilac.api.tick.ITickHandler
import opekope2.lilac.api.tick.ITickNotifier
import opekope2.optigui.api.IOptiGuiApi
import opekope2.optigui.api.interaction.*
import opekope2.optigui.filter.IFilter

internal object TextureReplacer : ClientModInitializer, IInteractor, IInspector by InteractionHolder {
    private object InteractionHolder : ClientPlayConnectionEvents.Disconnect, ITickHandler, IInspector {
        val replacementCache = mutableMapOf<Identifier, Identifier>()

        var interacting: Boolean = false
            private set

        private val optiguiApi = IOptiGuiApi.getImplementation()

        private var container: Identifier? = null
        private var target: IInteractionTarget? = null
        private var raw: RawInteraction? = null
        private var data: IInteractionData? = null
        private var screen: Screen? = null

        var riddenEntity: Entity? = null

        fun refreshInteractionData() {
            val newData = target?.computeInteractionData() ?: riddenEntity?.let {
                IEntityProcessor.ofClass(it.javaClass)?.apply(it)
            }

            if (newData != data) {
                data = newData
                replacementCache.clear()
            }
        }

        fun prepare(container: Identifier, target: IInteractionTarget, rawInteraction: RawInteraction): Boolean {
            if (interacting) return false

            this.container = container
            this.target = target
            this.raw = rawInteraction

            return true
        }

        fun begin(screen: Screen) {
            this.screen = screen

            interacting = true

            refreshInteractionData()
        }

        fun end() {
            container = null
            target = null
            raw = null
            data = null

            interacting = false

            replacementCache.clear()
        }

        fun createInteraction(texture: Identifier): Interaction? {
            return if (!interacting) null
            else Interaction(container ?: return null, texture, screen?.title ?: return null, raw, data)
        }

        override fun onPlayDisconnect(handler: ClientPlayNetworkHandler, client: MinecraftClient) {
            // Clean up, don't leak memory. Just to be safe.
            end()
            riddenEntity = null
        }

        override fun onTick(world: World, real: Boolean) {
            if (interacting && world.isClient) {
                refreshInteractionData()
            }
        }

        override fun inspectCurrentInteraction(): String? {
            if (!interacting) return null

            val container = container ?: return null
            val texture = optiguiApi.getContainerTexture(container)
            val raw = raw ?: return null
            val data = data ?: return null

            val target = target ?: return null

            return buildString {
                appendLine("# Generated by OptiGUI Inspector")
                appendLine()

                appendLine("# You may not need all selectors")
                appendLine("[$container]")
                appendLine(
                    if (texture != null) "interaction.texture=$texture"
                    else "#interaction.texture: default texture is unavailable"
                )
                appendLine("interaction.hand=${raw.hand.name.lowercase()}")
                data.writeSelectors { key, value -> appendLine("$key=$value") }
                appendLine()

                when (target) {
                    is IInteractionTarget.BlockEntityTarget -> {
                        appendLine("# NBT (for more detailed inspection purposes, as there is no selector for NBT):")
                        appendLine("# ${target.blockEntity.createNbt()}")
                        appendLine()
                    }

                    is IInteractionTarget.EntityTarget -> {
                        appendLine("# NBT (for more detailed inspection purposes, as there is no selector for NBT):")
                        appendLine("# ${target.entity.writeNbt(NbtCompound())}")
                        appendLine()
                    }

                    else -> {
                        appendLine("# NBT is not available")
                        appendLine()
                    }
                }

                appendLine("# If you have an idea or feedback about the inspector, feel free to share it at")
                appendLine("# https://github.com/opekope2/OptiGUI/issues/72")
            }
        }
    }

    internal var filter: IFilter<Interaction, Identifier> = IFilter<Interaction, Identifier> { IFilter.Result.skip() }
    internal var replaceableTextures = setOf<Identifier>()

    @JvmStatic
    var riddenEntity: Entity? by InteractionHolder::riddenEntity

    override fun onInitializeClient() {
        ClientPlayConnectionEvents.DISCONNECT.register(InteractionHolder)
        ITickNotifier.getInstance() += InteractionHolder
    }

    @JvmStatic
    fun replaceTexture(texture: Identifier): Identifier {
        // Only replace predefined textures
        if (texture !in replaceableTextures) return texture

        // Don't bother replacing textures if not interacting
        val interaction = InteractionHolder.createInteraction(texture) ?: return texture

        return InteractionHolder.replacementCache.computeIfAbsent(texture) {
            filter.evaluate(interaction).let { (it as? IFilter.Result.Match)?.result } ?: texture
        }
    }

    private inline val Screen.isRetexturable: Boolean
        get() = IOptiGuiApi.getImplementation().isScreenRetexturable(this)

    @JvmStatic
    fun handleScreenChange(screen: Screen?) =
        if (screen?.isRetexturable == true) InteractionHolder.begin(screen)
        else InteractionHolder.end()

    override fun interact(
        container: Identifier,
        player: PlayerEntity,
        world: World,
        hand: Hand,
        target: IInteractionTarget,
        hitResult: HitResult?
    ): Boolean {
        if (!world.isClient) return false
        return InteractionHolder.prepare(container, target, RawInteraction(player, world, hand, hitResult))
    }
}
