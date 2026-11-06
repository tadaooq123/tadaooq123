package com.github.tadaooq123.bdm;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Сервер сообщает клиенту его текущую роль (-1 — нет роли). */
public record RolePayload(byte role) implements CustomPacketPayload {

    public static final Type<RolePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BdmMod.MOD_ID, "role"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RolePayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.BYTE, RolePayload::role, RolePayload::new);

    public static RolePayload of(Role role) {
        return new RolePayload((byte) (role == null ? -1 : role.ordinal()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
