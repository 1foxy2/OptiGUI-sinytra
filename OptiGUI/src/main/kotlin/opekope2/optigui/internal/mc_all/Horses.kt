package opekope2.optigui.internal.mc_all

import net.minecraft.entity.Entity
import net.minecraft.entity.mob.SkeletonHorseEntity
import net.minecraft.entity.mob.ZombieHorseEntity
import net.minecraft.entity.passive.*
import net.minecraft.util.Nameable
import opekope2.filter.*
import opekope2.optigui.internal.properties.HorseProperties
import opekope2.optigui.provider.RegistryLookup
import opekope2.optigui.provider.getProvider
import opekope2.optigui.resource.Resource
import opekope2.util.TexturePath
import opekope2.util.splitIgnoreEmpty

private const val CONTAINER = "horse"
private val texture = TexturePath.HORSE

fun createHorseFilter(resource: Resource): FilterInfo? {
    if (resource.properties["container"] != CONTAINER) return null
    val replacement = findReplacementTexture(resource) ?: return null

    val filters = createGeneralFilters(resource, CONTAINER, texture)

    filters.addForProperty(resource, "variants", { it.splitIgnoreEmpty(*delimiters) }) { variants ->
        val variantFilter = ContainingFilter(variants)

        Filter {
            variantFilter.evaluate(
                (it.data as? HorseProperties)?.variant ?: return@Filter FilterResult.Mismatch()
            )
        }
    }

    return FilterInfo(
        PostProcessorFilter(ConjunctionFilter(filters), replacement),
        setOf(texture)
    )
}

internal fun processHorse(horse: Entity): Any? {
    if (horse !is AbstractHorseEntity) return null
    val lookup = getProvider<RegistryLookup>()

    val world = horse.world ?: return null

    val variant = when (horse) {
        is HorseEntity -> "horse"
        is DonkeyEntity -> "donkey"
        is MuleEntity -> "mule"
        is LlamaEntity -> "llama" // Includes trader llama
        is CamelEntity -> "_camel"
        is ZombieHorseEntity -> "_zombie_horse"
        is SkeletonHorseEntity -> "_skeleton_horse"
        else -> return null
    }

    return HorseProperties(
        container = CONTAINER,
        texture = texture,
        name = (horse as? Nameable)?.customName?.string,
        biome = lookup.lookupBiome(world, horse.blockPos),
        height = horse.blockPos.y,
        variant = variant
    )
}