package dev.bytephase.pvp.marlow;

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Retains the supplied optimizer's version, challenge and server opt-out protocol. */
public final class MarlowNetworking {
    private static boolean initialized;
    private MarlowNetworking() { }

    public static void initialize() {
        if (initialized || FabricLoader.getInstance().isModLoaded("marlowcrystal")) return;
        initialized = true;
        PayloadTypeRegistry.configurationS2C().register(OptOut.TYPE, OptOut.CODEC);
        PayloadTypeRegistry.playS2C().register(OptOut.TYPE, OptOut.CODEC);
        PayloadTypeRegistry.playS2C().register(Challenge.TYPE, Challenge.CODEC);
        PayloadTypeRegistry.playC2S().register(Acknowledgment.TYPE, Acknowledgment.CODEC);
        PayloadTypeRegistry.playC2S().register(Version.TYPE, Version.CODEC);
        PayloadTypeRegistry.playC2S().register(Response.TYPE, Response.CODEC);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> announce());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> MarlowCrystal.get().serverSession().disconnected());
        ClientConfigurationConnectionEvents.DISCONNECT.register((handler, client) -> MarlowCrystal.get().serverSession().disconnected());
        ClientPlayNetworking.registerGlobalReceiver(OptOut.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    MarlowCrystal.get().serverSession().optOut();
                    if (ClientPlayNetworking.canSend(Acknowledgment.TYPE)) ClientPlayNetworking.send(Acknowledgment.INSTANCE);
                }));
        ClientPlayNetworking.registerGlobalReceiver(Challenge.TYPE, (payload, context) -> {
            if (ClientPlayNetworking.canSend(Response.TYPE)) ClientPlayNetworking.send(new Response(payload.id()));
        });
    }

    public static void announce() {
        if (initialized && MarlowCrystal.enabled() && Minecraft.getInstance().getConnection() != null
                && !Minecraft.getInstance().isSingleplayer() && ClientPlayNetworking.canSend(Version.TYPE)) {
            ClientPlayNetworking.send(new Version(2, 0, 0));
        }
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String name) {
        return new CustomPacketPayload.Type<>(Identifier.parse("marlowcrystal:" + name));
    }

    public record OptOut() implements CustomPacketPayload {
        public static final Type<OptOut> TYPE = MarlowNetworking.type("opt_out");
        private static final OptOut INSTANCE = new OptOut();
        public static final StreamCodec<FriendlyByteBuf, OptOut> CODEC = new StreamCodec<>() {
            public OptOut decode(FriendlyByteBuf buffer) { return INSTANCE; }
            public void encode(FriendlyByteBuf buffer, OptOut value) { }
        };
        public Type<OptOut> type() { return TYPE; }
    }

    public record Acknowledgment() implements CustomPacketPayload {
        public static final Type<Acknowledgment> TYPE = MarlowNetworking.type("opt_out_ack");
        public static final Acknowledgment INSTANCE = new Acknowledgment();
        public static final StreamCodec<FriendlyByteBuf, Acknowledgment> CODEC = new StreamCodec<>() {
            public Acknowledgment decode(FriendlyByteBuf buffer) { return INSTANCE; }
            public void encode(FriendlyByteBuf buffer, Acknowledgment value) { }
        };
        public Type<Acknowledgment> type() { return TYPE; }
    }

    public record Challenge(int id) implements CustomPacketPayload {
        public static final Type<Challenge> TYPE = MarlowNetworking.type("challenge");
        public static final StreamCodec<FriendlyByteBuf, Challenge> CODEC = new StreamCodec<>() {
            public Challenge decode(FriendlyByteBuf buffer) { return new Challenge(buffer.readInt()); }
            public void encode(FriendlyByteBuf buffer, Challenge value) { buffer.writeInt(value.id()); }
        };
        public Type<Challenge> type() { return TYPE; }
    }

    public record Response(int id) implements CustomPacketPayload {
        public static final Type<Response> TYPE = MarlowNetworking.type("challenge_response");
        public static final StreamCodec<FriendlyByteBuf, Response> CODEC = new StreamCodec<>() {
            public Response decode(FriendlyByteBuf buffer) { return new Response(buffer.readInt()); }
            public void encode(FriendlyByteBuf buffer, Response value) { buffer.writeInt(value.id()); }
        };
        public Type<Response> type() { return TYPE; }
    }

    public record Version(int major, int minor, int patch) implements CustomPacketPayload {
        public static final Type<Version> TYPE = MarlowNetworking.type("version");
        public static final StreamCodec<FriendlyByteBuf, Version> CODEC = new StreamCodec<>() {
            public Version decode(FriendlyByteBuf buffer) {
                Version version = new Version(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
                buffer.readBoolean();
                buffer.skipBytes(buffer.readableBytes());
                return version;
            }
            public void encode(FriendlyByteBuf buffer, Version value) {
                buffer.writeVarInt(value.major()).writeVarInt(value.minor()).writeVarInt(value.patch());
                buffer.writeBoolean(false);
                buffer.writeVarInt(0); // Fabric
                buffer.writeUtf("", 40);
                buffer.writeBoolean(true); // Adapted implementation
                buffer.writeLong(0);
            }
        };
        public Type<Version> type() { return TYPE; }
    }
}
