package me.zziger.mphardcore;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerLivesState extends PersistentState {
    static public class PlayerData {
        public static final Codec<PlayerData> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.INT.fieldOf("livesLeft").forGetter(data -> data.livesLeft),
                        Codec.INT.optionalFieldOf("rescuedTimes", 0).forGetter(data -> data.rescuedTimes)
                ).apply(instance, PlayerData::new)
        );

        public int livesLeft;
        public int rescuedTimes = 0;

        PlayerData(int livesLeft) {
            this(livesLeft, 0);
        }

        PlayerData(int livesLeft, int rescuedTimes) {
            this.livesLeft = livesLeft;
            this.rescuedTimes = rescuedTimes;
        }
    }

    public static final Codec<PlayerLivesState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.unboundedMap(Codec.STRING.xmap(UUID::fromString, UUID::toString), PlayerData.CODEC)
                            .fieldOf("players")
                            .forGetter(state -> state.players)
            ).apply(instance, PlayerLivesState::new)
    );

    private static final PersistentStateType<PlayerLivesState> TYPE = new PersistentStateType<>(
            MultiplayerHardcore.MOD_ID,
            PlayerLivesState::new,
            CODEC,
            null
    );

    public HashMap<UUID, PlayerData> players = new HashMap<>();

    public PlayerLivesState() {
    }

    private PlayerLivesState(Map<UUID, PlayerData> players) {
        this.players.putAll(players);
    }
    public static PlayerLivesState getServerState(MinecraftServer server) {
        // (Note: arbitrary choice to use 'World.OVERWORLD' instead of 'World.END' or 'World.NETHER'.  Any work)
        PersistentStateManager persistentStateManager = server.getWorld(World.OVERWORLD).getPersistentStateManager();

        // The first time the following 'getOrCreate' function is called, it creates a brand new 'StateSaverAndLoader' and
        // stores it inside the 'PersistentStateManager'. The subsequent calls to 'getOrCreate' pass in the saved
        // 'StateSaverAndLoader' NBT on disk to our function 'StateSaverAndLoader::createFromNbt'.
        PlayerLivesState state = persistentStateManager.getOrCreate(TYPE);

        // If state is not marked dirty, when Minecraft closes, 'writeNbt' won't be called and therefore nothing will be saved.
        // Technically it's 'cleaner' if you only mark state as dirty when there was actually a change, but the vast majority
        // of mod writers are just going to be confused when their data isn't being saved, and so it's best just to 'markDirty' for them.
        // Besides, it's literally just setting a bool to true, and the only time there's a 'cost' is when the file is written to disk when
        // there were no actual change to any of the mods state (INCREDIBLY RARE).
        state.markDirty();

        return state;
    }

    public static PlayerData getPlayerState(LivingEntity player) {
        PlayerLivesState serverState = getServerState(player.getWorld().getServer());
        PlayerData playerState = serverState.players.computeIfAbsent(player.getUuid(), uuid -> new PlayerData(MultiplayerHardcoreConfig.defaultLives));

        return playerState;
    }

    public static PlayerData getPlayerState(MinecraftServer server, GameProfile player) {
        PlayerLivesState serverState = getServerState(server);
        PlayerData playerState = serverState.players.getOrDefault(player.getId(), new PlayerData(MultiplayerHardcoreConfig.defaultLives));

        return playerState;
    }
}
