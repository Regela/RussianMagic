/*package ru.rikgela.russianmagic

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.text.StringTextComponent
import net.minecraftforge.event.entity.EntityJoinWorldEvent
import net.minecraftforge.event.entity.living.LivingDeathEvent
import net.minecraftforge.event.entity.player.EntityItemPickupEvent
import net.minecraftforge.eventbus.api.SubscribeEvent

class MyForgeEventHandler {
    @SubscribeEvent
    fun pickupItem(e: EntityItemPickupEvent) {
        println("Item picked up!")
        if (e.entity is PlayerEntity) {
            (e.entity as PlayerEntity)
                    .sendMessage(StringTextComponent("You picked up something"))
        }
    }

    @SubscribeEvent
    fun onJoin(e: EntityJoinWorldEvent) {
        if (e.entity is PlayerEntity) {
            (e.entity as PlayerEntity)
                    .sendMessage(StringTextComponent("Hello from RussianMagic!"))
        }
    }

    @SubscribeEvent
    fun onDeath(e: LivingDeathEvent) {
        if (e.entity is PlayerEntity) {
            (e.entity as PlayerEntity)
                    .sendMessage(StringTextComponent("Земля тебе пуховик, бро!"))
        }
    }
}
*/
