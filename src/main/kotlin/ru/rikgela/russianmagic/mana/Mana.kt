package ru.rikgela.russianmagic.mana

import net.minecraft.client.Minecraft
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.ServerPlayerEntity
import net.minecraft.nbt.ByteArrayNBT
import net.minecraft.nbt.INBT
import net.minecraft.nbt.IntNBT
import net.minecraft.network.PacketBuffer
import net.minecraft.util.Direction
import net.minecraft.util.ResourceLocation
import net.minecraft.util.text.StringTextComponent
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.Capability.IStorage
import net.minecraftforge.common.capabilities.CapabilityInject
import net.minecraftforge.common.capabilities.ICapabilitySerializable
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.event.AttachCapabilitiesEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.LogicalSide
import net.minecraftforge.fml.network.NetworkEvent
import net.minecraftforge.fml.network.PacketDistributor
import ru.rikgela.russianmagic.MOD_ID
import ru.rikgela.russianmagic.common.RMNetworkChannel
import java.lang.Float.floatToIntBits
import java.lang.Float.intBitsToFloat
import java.lang.Integer.max
import java.util.function.Supplier


interface IMana {

    fun consume(points: Int, player: ServerPlayerEntity): Boolean
    fun fill(points: Int)
    fun setMana(points: Int)
    fun setMaxMana(points: Int)
    fun setManaPerTick(points: Float)
    fun sendToPlayer(player: ServerPlayerEntity)
    fun copy(mana: IMana)
    fun tick()
    fun toByteArray(): ByteArray
    fun loadFromByteArray(buff: ByteArray)
    val currentMana: Int
    val maxMana: Int
    val manaPerTick: Float
}

class Mana : IMana {
    companion object {
        fun fromPlayer(player: PlayerEntity): Mana {
            if (MANA_CAP != null) {
                return player.getCapability(MANA_CAP!!).orElse(Mana()) as Mana
            }
            return Mana()
        }
    }

    override fun sendToPlayer(player: ServerPlayerEntity) {
        RMNetworkChannel.send(PacketDistributor.PLAYER.with { player }, ManaMessage(this))
    }

    override fun copy(mana: IMana) {
        this.currentMana = mana.currentMana
    }

    override fun tick() {
        if (ticks % 20 == 0) {
            fill(max((20 * manaPerTick).toInt(), 1))
        }
        if (ticks % 100 == 0) {
            manaPerTick = (manaPerTick * 1.1).toFloat()
        }
        ticks++
    }

    override fun toByteArray(): ByteArray {
        val manaPerTick = floatToIntBits(this.manaPerTick)
        return byteArrayOf(
                ((currentMana ushr 24) and 0xFFFF).toByte(),
                ((currentMana ushr 16) and 0xFFFF).toByte(),
                ((currentMana ushr 8) and 0xFFFF).toByte(),
                (currentMana and 0xFFFF).toByte(),
                ((maxMana ushr 24) and 0xFFFF).toByte(),
                ((maxMana ushr 16) and 0xFFFF).toByte(),
                ((maxMana ushr 8) and 0xFFFF).toByte(),
                (maxMana and 0xFFFF).toByte(),
                ((manaPerTick ushr 24) and 0xFFFF).toByte(),
                ((manaPerTick ushr 16) and 0xFFFF).toByte(),
                ((manaPerTick ushr 8) and 0xFFFF).toByte(),
                (manaPerTick and 0xFFFF).toByte()
        )
    }

    override fun loadFromByteArray(buff: ByteArray) {
        var i = 0
        currentMana = buff[i++].toInt() shl 24 or
                (buff[i++].toInt() and 0xFF shl 16) or
                (buff[i++].toInt() and 0xFF shl 8) or
                (buff[i++].toInt() and 0xFF)

        maxMana = buff[i++].toInt() shl 24 or
                (buff[i++].toInt() and 0xFF shl 16) or
                (buff[i++].toInt() and 0xFF shl 8) or
                (buff[i++].toInt() and 0xFF)

        val manaPerTickBits = buff[i++].toInt() shl 24 or
                (buff[i++].toInt() and 0xFF shl 16) or
                (buff[i++].toInt() and 0xFF shl 8) or
                (buff[i].toInt() and 0xFF)
        manaPerTick = intBitsToFloat(manaPerTickBits)
    }

    override var currentMana = 250
        private set
    override var maxMana = 1000
        private set
    override var manaPerTick = 1F
        private set
    private var ticks = 0

    override fun consume(points: Int, player: ServerPlayerEntity): Boolean {
        if (currentMana >= points) {
            currentMana -= points
            sendToPlayer(player)
            return true
        }
        return false
    }

    override fun fill(points: Int) {
        currentMana += points
        if (currentMana > maxMana) {
            currentMana = maxMana
        }
    }

    override fun setMana(points: Int) {
        currentMana = points
    }

    override fun setMaxMana(points: Int) {
        maxMana = points
    }

    override fun setManaPerTick(points: Float) {
        manaPerTick = points
    }

}

class ManaMessage(
        private val mana: Mana
) {
    companion object {
        val minecraft: Minecraft = Minecraft.getInstance()
        fun fromPacketBuffer(pb: PacketBuffer): ManaMessage {
            val ret = Mana()
            ret.loadFromByteArray(pb.readByteArray())
            return ManaMessage(ret)
        }
    }

    fun encoder(pb: PacketBuffer) {
        pb.writeByteArray(mana.toByteArray())
    }

    fun handle(ctx: Supplier<NetworkEvent.Context?>) {
        ctx.get()?.enqueueWork {
            MANA_CAP?.let { minecraft.player?.getCapability(it)?.orElse(Mana())?.copy(mana) }
        }
        ctx.get()?.packetHandled = true
    }
}

class ManaStorage : IStorage<IMana> {
    override fun writeNBT(capability: Capability<IMana>, instance: IMana, side: Direction?): INBT {
        return ByteArrayNBT(instance.toByteArray())
    }

    override fun readNBT(capability: Capability<IMana>, instance: IMana, side: Direction?, nbt: INBT) {
        if (nbt is ByteArrayNBT) {
            instance.loadFromByteArray(nbt.byteArray)
        }
    }
}

@CapabilityInject(IMana::class)
var MANA_CAP: Capability<IMana>? = null

class ManaProvider : ICapabilitySerializable<INBT> {
    private val instance: IMana? = MANA_CAP?.defaultInstance

    @Override
    fun hasCapability(capability: Capability<Any>, facing: Direction): Boolean {
        return capability == MANA_CAP
    }

    override fun <T : Any?> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> {
        return if (cap == MANA_CAP) LazyOptional.of { instance as T } else LazyOptional.empty()
    }

    override fun deserializeNBT(nbt: INBT?) {
        MANA_CAP?.storage?.readNBT(MANA_CAP, this.instance, null, nbt)
    }

    override fun serializeNBT(): INBT {
        return MANA_CAP?.storage?.writeNBT(MANA_CAP, this.instance, null) ?: IntNBT.valueOf(0)
    }
}

class ManaCapabilityHandler {
    @SubscribeEvent
    fun attachCapability(event: AttachCapabilitiesEvent<Entity>) {
        event.addCapability(MANA_CAP, ManaProvider())
    }
    companion object {
        val MANA_CAP = ResourceLocation(MOD_ID, "mana")
    }
}

class ManaEventHandler {
    @SubscribeEvent
    fun onPlayerLogsIn(event: PlayerLoggedInEvent) {
        val player: PlayerEntity = event.player
        if (MANA_CAP != null) {
            val mana: IMana = player.getCapability(MANA_CAP!!, null).orElse(Mana())
            val message = String.format("Hello there, you have §7%d§r mana left.", mana.currentMana)
            player.sendMessage(StringTextComponent(message))
            if (player is ServerPlayerEntity)
                mana.sendToPlayer(player)
        } else {
            player.sendMessage(StringTextComponent("Mana not registered!"))
        }
    }

    @SubscribeEvent
    fun onPlayerTick(event: TickEvent.PlayerTickEvent) {
        if (event.side == LogicalSide.SERVER && event.phase == TickEvent.Phase.END) {
            val mana = Mana.fromPlayer(event.player)
            mana.tick()
            mana.sendToPlayer(event.player as ServerPlayerEntity)
        }
    }
}