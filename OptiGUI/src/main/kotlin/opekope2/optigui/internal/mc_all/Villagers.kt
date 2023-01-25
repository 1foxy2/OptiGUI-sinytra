package opekope2.optigui.internal.mc_all

import net.minecraft.entity.Entity
import net.minecraft.entity.passive.VillagerEntity
import net.minecraft.entity.passive.WanderingTraderEntity
import net.minecraft.util.Identifier
import net.minecraft.util.Nameable
import opekope2.filter.*
import opekope2.optigui.interaction.Interaction
import opekope2.optigui.internal.properties.VillagerProperties
import opekope2.optigui.provider.RegistryLookup
import opekope2.optigui.provider.getProvider
import opekope2.optigui.resource.Resource
import opekope2.util.TexturePath
import opekope2.util.parseProfession
import opekope2.util.splitIgnoreEmpty

private const val CONTAINER = "villager"
private val texture = TexturePath.VILLAGER2
private val wanderingTraderProfession = Identifier(Identifier.DEFAULT_NAMESPACE, "_wandering_trader")

internal fun createVillagerFilter(resource: Resource): FilterInfo? {
    if (resource.properties["container"] != CONTAINER) return null
    val replacement = findReplacementTexture(resource) ?: return null

    val filters = createGeneralFilters(resource, CONTAINER, texture)

    filters.addForProperty(
        resource,
        "professions",
        { it.splitIgnoreEmpty(*delimiters).mapNotNull(::parseProfession) }
    ) { professions ->
        val professionFilters: Collection<Filter<Interaction, Unit>> = professions.map { (profession, levels) ->
            val professionFilter = EqualityFilter(profession)
            val levelFilter = DisjunctionFilter(levels.mapNotNull { it.toFilter() })

            ConjunctionFilter(
                Filter {
                    professionFilter.evaluate(
                        (it.data as? VillagerProperties)?.profession ?: return@Filter FilterResult.Mismatch()
                    )
                },
                Filter {
                    levelFilter.evaluate(
                        (it.data as? VillagerProperties)?.level ?: return@Filter FilterResult.Mismatch()
                    )
                }
            )
        }

        DisjunctionFilter(professionFilters)
    }

    return FilterInfo(
        PostProcessorFilter(ConjunctionFilter(filters), replacement),
        setOf(texture)
    )
}

internal fun processVillager(villager: Entity): Any? {
    if (villager !is VillagerEntity && villager !is WanderingTraderEntity) return null
    val lookup = getProvider<RegistryLookup>()

    val world = villager.world ?: return null
    val villagerData = (villager as? VillagerEntity)?.villagerData

    return VillagerProperties(
        container = CONTAINER,
        texture = texture,
        name = (villager as? Nameable)?.customName?.string,
        biome = lookup.lookupBiome(world, villager.blockPos),
        height = villager.blockPos.y,
        profession = villagerData?.profession?.let { lookup.lookupVillagerProfessionId(it) }
            ?: wanderingTraderProfession,
        level = villagerData?.level
            ?: 1 // Wandering trader default level, because it can't level up
    )
}