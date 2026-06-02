package com.yungnickyoung.minecraft.ribbits.entity;

import com.yungnickyoung.minecraft.ribbits.data.RibbitData;
import com.yungnickyoung.minecraft.ribbits.data.RibbitInstrument;
import com.yungnickyoung.minecraft.ribbits.entity.goal.*;
import com.yungnickyoung.minecraft.ribbits.module.*;
import com.yungnickyoung.minecraft.ribbits.util.GeoIP;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.util.GeckoLibUtil;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

public class RibbitEntity extends AgeableMob implements
        GeoEntity,
        Merchant {
    private static final int BED_HOME_SEARCH_RANGE = 48;
    private static final int BED_HOME_PATHFIND_BATCH_SIZE = 5;
    private static final int BED_HOME_SEARCH_RETRY_MIN_TICKS = 20;
    private static final int BED_HOME_SEARCH_RETRY_JITTER_TICKS = 20;
    private static final long VANILLA_DAY_PERIOD_TICKS = 24000L;
    private static final long VANILLA_NIGHT_MARKER_TICK = 13000L;
    private static final int BED_HOME_UNAVAILABLE_RETRY_MIN_TICKS = 600;
    private static final int BED_HOME_UNAVAILABLE_RETRY_JITTER_TICKS = 100;
    private static final int BED_HOME_PENDING_RETRY_MIN_TICKS = 300;
    private static final int BED_HOME_PENDING_RETRY_JITTER_TICKS = 300;
    private static final int SHELTER_PATH_RETRY_MIN_TICKS = 40;
    private static final int SHELTER_PATH_RETRY_JITTER_TICKS = 40;
    private static final int SHELTER_PATH_RETRY_MAX_TICKS = 400;
    private static final int BED_HOME_MAX_PATH_FAILURES = 3;
    private static final int BED_HOME_UPPER_BUNK_MAX_PATH_FAILURES = 6;
    private static final int AUTOMATIC_BED_HOME_MAX_DISTANCE = 128;
    private static final int BED_HOME_VERTICAL_RANGE = 12;
    private static final int BED_HOME_BASIC_FLOOR_VERTICAL_DISTANCE = 3;
    private static final int BED_HOME_INITIAL_SEARCH_SPREAD_TICKS = 20;
    private static final int NIGHT_COMMUNITY_ANCHOR_SEARCH_RANGE = 96;
    private static final int NIGHT_SHELTER_WAIT_CARPET_SEARCH_RANGE = 48;
    private static final int NIGHT_SHELTER_WAIT_CARPET_PATHFIND_BATCH_SIZE = 5;
    private static final int NIGHT_SHELTER_WAIT_MAX_PATH_FAILURES = 3;
    private static final int LOCAL_NIGHT_SHELTER_WAIT_RETRY_TICKS = 1200;
    private static final int LOCAL_NIGHT_SHELTER_BLOCKED_CONFIRMATION_CYCLES = 3;
    private static final double SHELTER_PATH_PROGRESS_DISTANCE_SQR = 4.0D;
    private static final double LOCAL_NIGHT_SHELTER_BLOCKED_POSITION_DISTANCE_SQR = 9.0D;
    private static final double BED_HOME_SNAP_HORIZONTAL_DISTANCE = 0.25D;
    private static final double BED_HOME_SNAP_VERTICAL_DISTANCE = 0.55D;
    private static final double BED_HOME_UPPER_BUNK_APPROACH_HORIZONTAL_DISTANCE = 0.85D;
    private static final double BED_HOME_UPPER_BUNK_APPROACH_VERTICAL_DISTANCE = 1.15D;
    private static final double BED_HOME_PARTIAL_PATH_MAX_HORIZONTAL_DISTANCE = 2.5D;
    private static final double NAVIGATION_BLOCKER_SEARCH_DISTANCE = 0.85D;
    private static final double NAVIGATION_BLOCKER_FORWARD_DOT = 0.2D;
    private static final double NAVIGATION_ENTITY_BLOCKER_AHEAD_DISTANCE = 0.65D;
    private static final double NAVIGATION_ENTITY_BLOCKER_CONTACT_MARGIN = 0.25D;
    private static final double NAVIGATION_ENTITY_BLOCKER_VERTICAL_MARGIN = 0.1D;
    private static final int NAVIGATION_ENTITY_BLOCKER_DETOUR_TICKS = 300;
    private static final int NAVIGATION_ENTITY_BLOCKER_MAX_DETOURS = 3;
    private static final int NAVIGATION_ENTITY_BLOCKER_RANDOM_RANGE = 8;
    private static final int NAVIGATION_ENTITY_BLOCKER_RANDOM_VERTICAL_RANGE = 3;
    private static final double NIGHT_SHELTER_WAIT_REACHED_DISTANCE = 0.9D;
    private static final double NIGHT_SHELTER_WAIT_CENTER_HORIZONTAL_DISTANCE = 0.12D;
    private static final double NIGHT_SHELTER_WAIT_CENTER_VERTICAL_DISTANCE = 0.35D;
    private static final double NIGHT_SHELTER_WAIT_OCCUPANCY_HORIZONTAL_DISTANCE = 0.45D;
    private static final double NIGHT_SHELTER_WAIT_OCCUPANCY_VERTICAL_DISTANCE = 0.75D;
    private static final double PLAYER_SET_HOME_REST_HORIZONTAL_DISTANCE = 0.9D;
    private static final double PLAYER_SET_HOME_REST_VERTICAL_DISTANCE = 1.0D;
    private static final double FLOATING_PLANT_NAVIGATION_NODE_REACHED_DISTANCE = 0.65D;
    private static final int DOOR_INTERACT_NODE_COOLDOWN_TICKS = 20;
    private static final double DOOR_CLOSE_FORGET_DISTANCE = 3.0D;
    private static final double DOOR_HOLD_FOR_OTHER_RIBBITS_DISTANCE = 2.0D;
    private static final double DEFAULT_STEP_HEIGHT = 0.6D;
    private static final int LEASH_REST_STILL_TICKS = 100;
    private static final double LEASH_REST_MOVING_DISTANCE_SQR = 0.0025D;

    private static final EntityDimensions RESTING_DIMENSIONS = EntityDimensions.scalable(0.5F, 0.4F).withEyeHeight(0.25F);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final RawAnimation IDLE = RawAnimation.begin().thenPlay("idle");
    private static final RawAnimation IDLE_HOLDING_1 = RawAnimation.begin().thenPlay("idle_holding_1");
    private static final RawAnimation IDLE_HOLDING_2 = RawAnimation.begin().thenPlay("idle_holding_2");
    private static final RawAnimation IDLE_HOLDING_HAT = RawAnimation.begin().thenPlay("idle_holding_hat");
    private static final RawAnimation IDLE_HOLDING_FISHERMAN = RawAnimation.begin().thenPlay("idle_holding_fisherman");
    private static final RawAnimation WALK = RawAnimation.begin().thenPlay("walk");
    private static final RawAnimation WALK_HOLDING_1 = RawAnimation.begin().thenPlay("walk_holding_1");
    private static final RawAnimation WALK_HOLDING_2 = RawAnimation.begin().thenPlay("walk_holding_2");
    private static final RawAnimation WALK_HOLDING_HAT = RawAnimation.begin().thenPlay("walk_holding_hat");
    private static final RawAnimation WALK_HOLDING_FISHERMAN = RawAnimation.begin().thenPlay("walk_holding_fisherman");
    private static final RawAnimation SORCERER_BUFF = RawAnimation.begin().thenPlay("spell");
    private static final RawAnimation SORCERER_BUFF_HOLDING = RawAnimation.begin().thenPlay("spell_holding");
    private static final RawAnimation SORCERER_SEATED_BUFF = RawAnimation.begin().thenPlay("resting_spell");
    private static final RawAnimation SORCERER_SEATED_BUFF_HOLDING = RawAnimation.begin().thenPlay("resting_spell_holding");
    private static final RawAnimation FISH = RawAnimation.begin().thenPlay("fishing");
    private static final RawAnimation FISH_HOLDING = RawAnimation.begin().thenPlay("fishing_holding");
    private static final RawAnimation REST = RawAnimation.begin().thenPlay("resting");
    private static final RawAnimation REST_HOLDING = RawAnimation.begin().thenPlay("resting_holding");
    private static final RawAnimation REST_PRIDE_HOLDING = RawAnimation.begin().thenPlay("resting_pride_holding");
    private static final RawAnimation REST_FISHERMAN = RawAnimation.begin().thenPlay("resting_fisherman");
    private static final RawAnimation REST_FISHERMAN_HOLDING = RawAnimation.begin().thenPlay("resting_fisherman_holding");
    private static final RawAnimation WATER_CROPS = RawAnimation.begin().thenPlay("water_crops");
    private static final RawAnimation WATER_CROPS_HOLDING = RawAnimation.begin().thenPlay("water_crops_holding");
    private static final RawAnimation FALLING = RawAnimation.begin().thenPlay("ribbit_fall");
    private static final RawAnimation FALLING_FISHERMAN = RawAnimation.begin().thenPlay("fisherman_ribbit_fall");

    @Nullable
    private Player tradingPlayer;
    @Nullable
    protected MerchantOffers offers;
    private long lastRestockGameTime;
    private int numberOfRestocksToday;
    private long lastRestockCheckDayTime;

    // How much to multiply movement speed when in water
    public static final float WATER_SPEED_MULTIPLIER = 2.0f;

    private final RibbitPlayMusicGoal musicGoal = new RibbitPlayMusicGoal(this, 1.0f, 2000, 3000);
    private final RibbitPrideParadeGoal prideParadeGoal = new RibbitPrideParadeGoal(this);
    private final RibbitWaterCropsGoal waterCropsGoal = new RibbitWaterCropsGoal(this, 16.0d, 1.0f, 1200);
    private final RibbitFishGoal fishGoal = new RibbitFishGoal(this, 16.0d, 1.0f, 600, 1800);
    private final RibbitApplyBuffGoal applyBuffGoal = new RibbitApplyBuffGoal(this, 32.0d, 12000);

    private static final EntityDataAccessor<RibbitData> RIBBIT_DATA = SynchedEntityData.defineId(RibbitEntity.class, EntityDataSerializerModule.RIBBIT_DATA_SERIALIZER);
    private static final EntityDataAccessor<Boolean> PLAYING_INSTRUMENT = SynchedEntityData.defineId(RibbitEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> UMBRELLA_FALLING = SynchedEntityData.defineId(RibbitEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> WATERING = SynchedEntityData.defineId(RibbitEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FISHING = SynchedEntityData.defineId(RibbitEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RESTING = SynchedEntityData.defineId(RibbitEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> BUFFING = SynchedEntityData.defineId(RibbitEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SEATED_BUFFING = SynchedEntityData.defineId(RibbitEntity.class, EntityDataSerializers.BOOLEAN);

    // These fields are used to prevent threadlocking by accessing entityData on rendering thread
    private RibbitData sidedRibbitData = new RibbitData(RibbitProfessionModule.NITWIT, RibbitUmbrellaTypeModule.UMBRELLA_1, RibbitInstrumentModule.NONE);
    private boolean isPlayingInstrument = false;
    private boolean isUmbrellaFalling = false;
    private boolean isWatering = false;
    private boolean isFishing = false;
    private boolean isResting = false;
    private boolean isBuffing = false;
    private boolean isSeatedBuffing = false;

    // NOTE: Fields below here are used only on Server
    private int ticksPlayingMusic;

    private BlockPos homePosition;
    private BlockPos dayActivityHomePosition;
    private BlockPos pendingBedHomePosition;
    private BlockPos pendingBedHomeApproachPosition;
    private BlockPos activeBedHomeApproachPosition;
    private boolean homePositionSetByPlayer;
    private boolean homePositionIsAutomaticBed;
    private int nextBedHomeSearchTick;
    private boolean initialBedHomeSearchScheduled;
    private BlockPos failingAutomaticBedHomePosition;
    private int automaticBedHomePathFailures;
    private BlockPos nightCommunityAnchorPosition;
    private BlockPos nightShelterWaitPosition;
    private boolean nightShelterWaitReached;
    private boolean localNightShelterWaitActive;
    private boolean lastNightShelterWaitSearchLocallyBlocked;
    private BlockPos lastLocalNightShelterBlockedPosition;
    private int localNightShelterBlockedCycles;
    @Nullable
    private BlockPos navigationEntityBlockerTarget;
    @Nullable
    private String navigationEntityBlockerContext;
    private int navigationEntityBlockerDetourCycles;
    private int navigationEntityBlockerDetourUntilTick;
    private boolean lastNightShelterWaitPathCanReach;
    private boolean lastNightShelterWaitPathProgressive;
    private boolean lastNightCommunityAnchorPathCanReach;
    private boolean lastNightCommunityAnchorPathProgressive;
    private int leashStillTicks;
    private boolean autonomousAiPausedLastTick;
    private final Set<BlockPos> shelterDoorsToClose = new HashSet<>();
    @Nullable
    private Node lastCheckedDoorNode;
    private int remainingDoorNodeCooldown;
    private final Map<BlockPos, ShelterPathRetry> unreachableBedHomeRetries = new HashMap<>();
    private final Map<BlockPos, ShelterPathRetry> unreachableNightShelterWaitRetries = new HashMap<>();
    private final Set<BlockPos> lastNightShelterWaitPathFailedCandidates = new LinkedHashSet<>();

    /**
     * Set of Ribbits playing music with this Ribbit as the master.
     * Only used if this Ribbit is the master.
     * Does not include the master Ribbit itself.
     */
    private Set<RibbitEntity> ribbitsPlayingMusic = new HashSet<>();
    private Set<Player> playersHearingMusic = new HashSet<>();
    private Set<RibbitInstrument> bandMembers = new HashSet<>();
    @Nullable
    private RibbitEntity masterRibbit;

    private int buffCooldown = 0;
    private int waterCropsCooldown = 0;

    public RibbitEntity(EntityType<RibbitEntity> entityType, Level level) {
        super(entityType, level);

        this.setPathfindingMalus(PathType.WATER, 0.0F);
        this.setPathfindingMalus(PathType.WATER_BORDER, 0.0F);
        this.getNavigation().setCanOpenDoors(true);
        this.getNavigation().setCanFloat(true);

        this.reassessGoals();
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        if (this.isResting) {
            return RESTING_DIMENSIONS.scale(this.getAgeScale());
        }

        return super.getDefaultDimensions(pose);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new RibbitTradeWithPlayerGoal(this));
        this.goalSelector.addGoal(0, new RibbitLookAtTradingPlayerGoal(this));
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new RibbitGoHomeGoal(this, 1.8f, 1f));
        this.goalSelector.addGoal(1, new RibbitWaitInShelterGoal(this, 1.0D));
        this.goalSelector.addGoal(2, new RibbitPanicGoal(this, 1.5D));
        this.goalSelector.addGoal(3, new RibbitStopAndStareAtFrogGoal(this, 4.0F));
        this.goalSelector.addGoal(4, new RibbitLookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RibbitStrollGoal(this, 1.0D, 12));
    }

    @Override
    public void aiStep() {
        super.aiStep();

        if (!this.level().isClientSide()) {
            if (this.tickCount % 600 == 0 && this.getHealth() < this.getMaxHealth()) {
                this.setHealth(Math.min(this.getHealth() + 1, this.getMaxHealth()));
            }

            if ((this.onGround() || this.isInWater()) && this.isUmbrellaFalling()) {
                this.setUmbrellaFalling(false);
            }

            if (this.fallDistance >= 2 || this.isUmbrellaFalling()) {
                Vec3 velocity = this.getDeltaMovement();
                this.resetFallDistance();
                this.setDeltaMovement(velocity.x, -0.1d, velocity.z);
                this.setUmbrellaFalling(true);
            }

            if (this.buffCooldown > 0) {
                this.buffCooldown--;
            }

            if (this.waterCropsCooldown > 0) {
                this.waterCropsCooldown--;
            }

            if (this.shouldRestock()) {
                this.restock();
            }

            this.tickAutonomousAiPauseState();
            if (!this.isAutonomousAiPaused()) {
                if (this.isAirborneForNightShelterActions()) {
                    this.clearLocalNightShelterWait();
                }

                this.clearAutomaticBedHomeIfTooFar();
                this.tryRestAtHomeBed();
                this.tickDoorNavigationAssist();
                this.tickShelterDoors();
                this.tickFloatingPlantNavigationAssist();
                this.tickBasicBedHomeState();
            }
            this.updateRestingPoseState();
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (this.getBuffing() && this.level().isClientSide()) {
            double radius = 1.25;
            for (float theta = 0; theta < Mth.TWO_PI; theta += this.random.nextFloat() * 0.8F + 0.5F) {
                this.level().addParticle((ParticleOptions) ParticleTypeModule.SPELL.get(),
                        this.getX() + Mth.cos(theta) * radius, this.getY(), this.getZ() + Mth.sin(theta) * radius,
                        0.0D, 0.0D, 0.0D);
            }
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(RIBBIT_DATA, new RibbitData(RibbitProfessionModule.NITWIT, RibbitUmbrellaTypeModule.UMBRELLA_1, RibbitInstrumentModule.NONE));
        builder.define(PLAYING_INSTRUMENT, false);
        builder.define(UMBRELLA_FALLING, false);
        builder.define(WATERING, false);
        builder.define(FISHING, false);
        builder.define(RESTING, false);
        builder.define(BUFFING, false);
        builder.define(SEATED_BUFFING, false);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput valueInput) {
        super.readAdditionalSaveData(valueInput);

        valueInput.read("RibbitData", RibbitData.CODEC)
                .ifPresent(this::setRibbitData);

        valueInput.read("Offers", MerchantOffers.CODEC)
                .ifPresent(offers -> this.offers = offers);

        Optional<Integer> homeX = valueInput.getInt("HomePosX");
        Optional<Integer> homeY = valueInput.getInt("HomePosY");
        Optional<Integer> homeZ = valueInput.getInt("HomePosZ");
        Optional<Integer> dayHomeX = valueInput.getInt("DayHomePosX");
        Optional<Integer> dayHomeY = valueInput.getInt("DayHomePosY");
        Optional<Integer> dayHomeZ = valueInput.getInt("DayHomePosZ");
        boolean homeSetByPlayer = valueInput.getBooleanOr("HomePosSetByPlayer", false);
        boolean automaticBedHome = valueInput.getBooleanOr("HomePosIsAutomaticBed", false);

        if (homeX.isPresent() && homeY.isPresent() && homeZ.isPresent()) {
            BlockPos savedHome = new BlockPos(homeX.get(), homeY.get(), homeZ.get());
            if (homeSetByPlayer) {
                this.setHomePosition(savedHome, false, true, false);
            } else if (automaticBedHome) {
                this.setHomePosition(savedHome, false, false, true);
            }
        }

        if (dayHomeX.isPresent() && dayHomeY.isPresent() && dayHomeZ.isPresent()) {
            this.setDayActivityHomePosition(new BlockPos(dayHomeX.get(), dayHomeY.get(), dayHomeZ.get()));
        } else if (this.dayActivityHomePosition == null) {
            this.setDayActivityHomePosition(this.blockPosition());
        }

        this.reassessGoals();
    }

    // NOTE: 若运行时出现 MerchantOffers 反序列化的注册表上下文问题，可改为手动从 valueInput.child("Offers") 取得子输入并结合 valueInput.lookup() 构造 RegistryOps 进行解码。
    @Override
    protected void addAdditionalSaveData(ValueOutput valueOutput) {
        super.addAdditionalSaveData(valueOutput);

        valueOutput.store("RibbitData", RibbitData.CODEC, this.getRibbitData());

        if (!this.level().isClientSide()) {
            MerchantOffers offers = this.getOffers();
            if (!offers.isEmpty()) {
                valueOutput.store("Offers", MerchantOffers.CODEC, offers);
            }
        }

        if (this.homePosition != null && (this.homePositionSetByPlayer || this.homePositionIsAutomaticBed)) {
            valueOutput.putInt("HomePosX", this.homePosition.getX());
            valueOutput.putInt("HomePosY", this.homePosition.getY());
            valueOutput.putInt("HomePosZ", this.homePosition.getZ());
            valueOutput.putBoolean("HomePosSetByPlayer", this.homePositionSetByPlayer);
            valueOutput.putBoolean("HomePosIsAutomaticBed", this.homePositionIsAutomaticBed);
        }

        if (this.dayActivityHomePosition != null) {
            valueOutput.putInt("DayHomePosX", this.dayActivityHomePosition.getX());
            valueOutput.putInt("DayHomePosY", this.dayActivityHomePosition.getY());
            valueOutput.putInt("DayHomePosZ", this.dayActivityHomePosition.getZ());
        }
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> dataAccessor) {
        super.onSyncedDataUpdated(dataAccessor);

        if (RIBBIT_DATA.equals(dataAccessor)) {
            this.sidedRibbitData = this.entityData.get(RIBBIT_DATA);
        } else if (UMBRELLA_FALLING.equals(dataAccessor)) {
            this.isUmbrellaFalling = this.entityData.get(UMBRELLA_FALLING);
        } else if (PLAYING_INSTRUMENT.equals(dataAccessor)) {
            this.isPlayingInstrument = this.entityData.get(PLAYING_INSTRUMENT);
        } else if (FISHING.equals(dataAccessor)) {
            this.isFishing = this.entityData.get(FISHING);
        } else if (RESTING.equals(dataAccessor)) {
            boolean wasResting = this.isResting;
            this.isResting = this.entityData.get(RESTING);
            if (wasResting != this.isResting) {
                this.refreshDimensions();
            }
        } else if (WATERING.equals(dataAccessor)) {
            this.isWatering = this.entityData.get(WATERING);
        } else if (BUFFING.equals(dataAccessor)) {
            this.isBuffing = this.entityData.get(BUFFING);
        } else if (SEATED_BUFFING.equals(dataAccessor)) {
            this.isSeatedBuffing = this.entityData.get(SEATED_BUFFING);
        }
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        EntitySpawnReason entitySpawnReason, @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, entitySpawnReason, groupData);
        this.setDayActivityHomePosition(this.blockPosition());
        this.reassessGoals();
        return data;
    }

    @Override
    public boolean removeWhenFarAway(double $$0) {
        return false;
    }

    @Override
    public @NotNull InteractionResult mobInteract(Player player, @NotNull InteractionHand interactionHand) {
        ItemStack itemStack = player.getItemInHand(interactionHand);

        if (player.isSecondaryUseActive() && itemStack.is(Items.AMETHYST_SHARD)) {
            this.setHomePosition(this.blockPosition(), true, true, false);

            if (!player.getAbilities().instabuild) {
                itemStack.shrink(1);
            }
        } else if (this.isAlive() && !this.isTrading() && !this.isSleeping()) {
            if (this.isBaby()) {
                return InteractionResult.PASS;
            }
            boolean bl = this.getOffers().isEmpty();

            if (bl) {
                return InteractionResult.PASS;
            }

            if (!this.level().isClientSide() && !this.offers.isEmpty()) {
                this.startTrading(player);
            }

            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, interactionHand);
    }

    private void setHomePosition(BlockPos homePosition, boolean showHearts, boolean setByPlayer, boolean automaticBed) {
        this.homePosition = homePosition.immutable();
        this.pendingBedHomePosition = null;
        this.pendingBedHomeApproachPosition = null;
        this.activeBedHomeApproachPosition = null;
        this.nightCommunityAnchorPosition = null;
        this.clearNightShelterWait();
        this.clearLocalNightShelterWait();
        this.clearLocalNightShelterBlockedSearch();
        this.homePositionSetByPlayer = setByPlayer;
        this.homePositionIsAutomaticBed = automaticBed;
        this.initialBedHomeSearchScheduled = false;
        this.clearAutomaticBedHomePathFailures();
        this.unreachableNightShelterWaitRetries.clear();

        if (setByPlayer) {
            this.setDayActivityHomePosition(homePosition);
        }

        if (showHearts) {
            this.level().broadcastEntityEvent(this, (byte) 12);
        }

        if (!this.level().isClientSide()) {
            this.resetHomeRelatedGoals(showHearts);
        }
    }

    private void setAutomaticBedHome(BlockPos homePosition) {
        this.setHomePosition(homePosition, false, false, true);
    }

    private void setPendingBedHome(BlockPos homePosition, BlockPos approachPosition) {
        this.pendingBedHomePosition = homePosition.immutable();
        this.pendingBedHomeApproachPosition = approachPosition.immutable();
        this.activeBedHomeApproachPosition = this.pendingBedHomeApproachPosition;
        this.initialBedHomeSearchScheduled = false;
    }

    private void resetHomeRelatedGoals(boolean blockBandRejoin) {
        this.fishGoal.resetTarget();
        this.waterCropsGoal.resetTarget();
        this.musicGoal.resetTarget(blockBandRejoin);
        this.prideParadeGoal.resetTarget();
        this.getNavigation().stop();
    }

    public boolean isAutonomousAiPaused() {
        return this.isPassenger() || this.isLeashed();
    }

    private void tickAutonomousAiPauseState() {
        boolean paused = this.isAutonomousAiPaused();
        if (paused && !this.autonomousAiPausedLastTick) {
            this.pauseAutonomousActions();
        }

        this.autonomousAiPausedLastTick = paused;
        this.tickLeashRestState();
    }

    private void pauseAutonomousActions() {
        this.pendingBedHomePosition = null;
        this.pendingBedHomeApproachPosition = null;
        this.activeBedHomeApproachPosition = null;
        this.clearNightShelterWait();
        this.clearLocalNightShelterWait();
        this.clearLocalNightShelterBlockedSearch();
        this.clearNightCommunityAnchor();
        this.unreachableBedHomeRetries.clear();
        this.unreachableNightShelterWaitRetries.clear();
        this.clearAutomaticBedHomePathFailures();
        this.resetHomeRelatedGoals(false);
        this.setUmbrellaFalling(false);
        this.setBuffing(false);
    }

    private void tickLeashRestState() {
        if (!this.isLeashed() || this.isPassenger()) {
            this.leashStillTicks = 0;
            return;
        }

        if (this.getNavigation().isDone()
                && this.getDeltaMovement().horizontalDistanceSqr() <= LEASH_REST_MOVING_DISTANCE_SQR) {
            this.leashStillTicks++;
        } else {
            this.leashStillTicks = 0;
        }
    }

    private boolean shouldRestWhileLeashed() {
        return this.isLeashed() && this.leashStillTicks >= LEASH_REST_STILL_TICKS;
    }

    public boolean tryAssignShelterHome() {
        if (!this.isShelterNight()
                || this.homePositionSetByPlayer
                || this.isAutonomousAiPaused()
                || !this.canUpdateNightShelterNavigationNow()) {
            return false;
        }

        this.clearAutomaticBedHomeIfTooFar();

        boolean retryingLocalNightShelterWait = this.localNightShelterWaitActive;
        if (retryingLocalNightShelterWait) {
            if (this.tickCount < this.nextBedHomeSearchTick) {
                this.getNavigation().stop();
                return false;
            }
        }

        if (this.hasActiveNightShelterWait()) {
            if (!this.isNightShelterWaitReadyToRetry()) {
                return false;
            }
        }

        if (this.scheduleInitialBedHomeSearchIfNeeded()) {
            return false;
        }

        if (this.tickCount < this.nextBedHomeSearchTick) {
            return false;
        }

        if (this.hasValidAutomaticBedHome() || this.hasValidPendingBedHome()) {
            return false;
        }

        if (this.pendingBedHomePosition != null) {
            this.pendingBedHomePosition = null;
            this.pendingBedHomeApproachPosition = null;
            this.activeBedHomeApproachPosition = null;
        }

        this.scheduleNextBedHomeSearch();
        BedHomeSearchResult bedHome = this.findNearbyBedHomeSlot();
        Optional<BedHomePathSelection> selectedBedHome = bedHome.selectedHome();
        if (selectedBedHome.isEmpty() && retryingLocalNightShelterWait) {
            selectedBedHome = this.findReopenableBlockedBedHomeSlot();
        }

        if (selectedBedHome.isPresent()) {
            this.clearNightCommunityAnchor();
            this.clearNightShelterWait();
            this.clearLocalNightShelterWait();
            this.clearLocalNightShelterBlockedSearch();
            BedHomePathSelection newHome = selectedBedHome.get();
            this.setPendingBedHome(newHome.slot(), newHome.approach());
        } else {
            if (bedHome.pendingClaims() > 0) {
                this.schedulePendingBedHomeSearch();
            } else {
                this.scheduleUnavailableBedHomeSearch();
            }

            this.updateNightShelterWaitTarget();
            if (!this.hasActiveNightShelterWait() && retryingLocalNightShelterWait) {
                this.updateReopenableNightShelterWaitTarget();
            }

            if (this.hasActiveNightShelterWait()) {
                this.clearNightCommunityAnchor();
                if (retryingLocalNightShelterWait
                        && !this.lastNightShelterWaitPathCanReach
                        && !this.lastNightShelterWaitPathProgressive) {
                    this.clearNightShelterWait();
                    this.delayLocalNightShelterWaitRetry();
                } else {
                    this.clearLocalNightShelterWait();
                }
            } else {
                this.updateNightCommunityAnchor();
                boolean canApproachAnchor = this.nightCommunityAnchorPosition != null
                        && this.canApproachNightCommunityAnchor();
                if (canApproachAnchor && this.lastNightCommunityAnchorPathCanReach) {
                    this.clearLocalNightShelterWait();
                    this.clearLocalNightShelterBlockedSearch();
                } else if (canApproachAnchor) {
                    if (this.recordLocalNightShelterBlockedSearchIfNeeded()) {
                        this.blockLastNightShelterWaitPathFailedCandidatesForNight();
                        this.activateLocalNightShelterWait();
                    } else if (retryingLocalNightShelterWait) {
                        this.delayLocalNightShelterWaitRetry();
                    } else {
                        this.clearLocalNightShelterWait();
                    }
                } else if (this.nightCommunityAnchorPosition != null || this.lastNightShelterWaitSearchLocallyBlocked) {
                    if (this.recordLocalNightShelterBlockedSearchIfNeeded()) {
                        this.blockLastNightShelterWaitPathFailedCandidatesForNight();
                    }
                    this.activateLocalNightShelterWait();
                } else if (retryingLocalNightShelterWait) {
                    this.delayLocalNightShelterWaitRetry();
                }
            }

        }

        return selectedBedHome.isPresent();
    }

    public boolean tryAssignShelterHomeWhileWaiting() {
        if (!this.isShelterNight()
                || this.homePositionSetByPlayer
                || this.isAutonomousAiPaused()
                || this.isVehicle()
                || this.isLeashed()
                || !this.canUpdateNightShelterNavigationNow()
                || !this.hasActiveNightShelterWait()
                || this.hasUsableHomePosition()) {
            return false;
        }

        if (this.pendingBedHomePosition != null) {
            this.pendingBedHomePosition = null;
            this.pendingBedHomeApproachPosition = null;
            this.activeBedHomeApproachPosition = null;
        }

        BedHomeSearchResult bedHome = this.findNearbyBedHomeSlot(false);
        if (bedHome.selectedHome().isEmpty()) {
            return false;
        }

        BedHomePathSelection newHome = bedHome.selectedHome().get();
        this.clearNightCommunityAnchor();
        this.clearNightShelterWait();
        this.getNavigation().stop();
        this.setPendingBedHome(newHome.slot(), newHome.approach());
        return true;
    }

    private boolean scheduleInitialBedHomeSearchIfNeeded() {
        if (this.initialBedHomeSearchScheduled
                || this.nextBedHomeSearchTick > this.tickCount
                || this.hasValidAutomaticBedHome()
                || this.hasValidPendingBedHome()) {
            return false;
        }

        this.initialBedHomeSearchScheduled = true;
        this.nextBedHomeSearchTick = this.tickCount + 1 + this.random.nextInt(BED_HOME_INITIAL_SEARCH_SPREAD_TICKS);
        return true;
    }

    private void scheduleNextBedHomeSearch() {
        this.nextBedHomeSearchTick = this.tickCount
                + BED_HOME_SEARCH_RETRY_MIN_TICKS
                + this.random.nextInt(BED_HOME_SEARCH_RETRY_JITTER_TICKS);
    }

    private void scheduleUnavailableBedHomeSearch() {
        this.nextBedHomeSearchTick = this.tickCount
                + BED_HOME_UNAVAILABLE_RETRY_MIN_TICKS
                + this.random.nextInt(BED_HOME_UNAVAILABLE_RETRY_JITTER_TICKS);
    }

    private void schedulePendingBedHomeSearch() {
        this.nextBedHomeSearchTick = this.tickCount
                + BED_HOME_PENDING_RETRY_MIN_TICKS
                + this.random.nextInt(BED_HOME_PENDING_RETRY_JITTER_TICKS);
    }

    private void updateNightCommunityAnchor() {
        if (!this.isShelterNight() || this.hasUsableHomePosition()) {
            this.clearNightCommunityAnchor();
            return;
        }

        AABB searchBox = this.getBoundingBox().inflate(
                NIGHT_COMMUNITY_ANCHOR_SEARCH_RANGE,
                BED_HOME_VERTICAL_RANGE,
                NIGHT_COMMUNITY_ANCHOR_SEARCH_RANGE);
        Optional<RibbitEntity> nearbyShelteredRibbit = this.level().getEntitiesOfClass(
                        RibbitEntity.class,
                        searchBox,
                        ribbit -> ribbit != this
                                && ribbit.isAlive()
                                && ribbit.hasCommittedShelterHome())
                .stream()
                .min(Comparator.comparingDouble(ribbit -> ribbit.distanceToSqr(this)));

        if (nearbyShelteredRibbit.isEmpty()) {
            this.clearNightCommunityAnchor();
            return;
        }

        BlockPos anchor = nearbyShelteredRibbit.get().getCommittedShelterHomePosition();
        if (!anchor.equals(this.nightCommunityAnchorPosition)) {
            this.nightCommunityAnchorPosition = anchor.immutable();
        }
    }

    private void clearNightCommunityAnchor() {
        this.nightCommunityAnchorPosition = null;
        this.lastNightCommunityAnchorPathCanReach = false;
        this.lastNightCommunityAnchorPathProgressive = false;
    }

    private boolean canApproachNightCommunityAnchor() {
        this.lastNightCommunityAnchorPathCanReach = false;
        this.lastNightCommunityAnchorPathProgressive = false;
        if (this.nightCommunityAnchorPosition == null) {
            return false;
        }

        Path path = this.getNavigation().createPath(this.nightCommunityAnchorPosition, 1);
        boolean progressivePath = path != null
                && !path.canReach()
                && this.isProgressivePartialNightCommunityAnchorPath(path);
        this.lastNightCommunityAnchorPathCanReach = path != null && path.canReach();
        this.lastNightCommunityAnchorPathProgressive = progressivePath;
        boolean canApproach = path != null && (path.canReach() || progressivePath);
        return canApproach;
    }

    private void updateNightShelterWaitTarget() {
        this.lastNightShelterWaitSearchLocallyBlocked = false;
        this.lastNightShelterWaitPathCanReach = false;
        this.lastNightShelterWaitPathProgressive = false;

        if (!this.isShelterNight() || this.hasUsableHomePosition()) {
            this.clearNightShelterWait();
            return;
        }

        if (this.nightShelterWaitPosition != null
                && this.isAvailableNightShelterWaitCarpet(this.nightShelterWaitPosition)) {
            return;
        }

        Optional<BlockPos> waitPosition = this.findNightShelterWaitPosition();
        if (waitPosition.isEmpty()) {
            this.clearNightShelterWait();
            return;
        }

        BlockPos newWaitPosition = waitPosition.get();
        if (!newWaitPosition.equals(this.nightShelterWaitPosition)) {
            this.nightShelterWaitPosition = newWaitPosition.immutable();
            this.nightShelterWaitReached = false;
        }
    }

    private Optional<BlockPos> findNightShelterWaitPosition() {
        BlockPos origin = this.blockPosition();
        BlockPos min = origin.offset(
                -NIGHT_SHELTER_WAIT_CARPET_SEARCH_RANGE,
                -BED_HOME_VERTICAL_RANGE,
                -NIGHT_SHELTER_WAIT_CARPET_SEARCH_RANGE);
        BlockPos max = origin.offset(
                NIGHT_SHELTER_WAIT_CARPET_SEARCH_RANGE,
                BED_HOME_VERTICAL_RANGE,
                NIGHT_SHELTER_WAIT_CARPET_SEARCH_RANGE);
        List<BlockPos> availableCarpets = new ArrayList<>();
        List<RibbitEntity> nearbyRibbits = this.getNightShelterWaitOccupantCandidates(min, max);
        this.clearExpiredNightShelterWaitPathRetries();
        this.lastNightShelterWaitPathFailedCandidates.clear();

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!this.level().getBlockState(pos).is(BlockTags.WOOL_CARPETS)) {
                continue;
            }

            if (!this.isBasicFloorNightShelterWaitCarpet(pos)) {
                continue;
            }

            if (!this.hasNightShelterWaitHeadroom(pos)) {
                continue;
            }

            if (!this.isNightShelterWaitInterior(pos)) {
                continue;
            }

            if (!this.hasNightShelterWaitBodySpace(pos)) {
                continue;
            }

            if (this.getNightShelterWaitOccupant(pos, nearbyRibbits).isPresent()) {
                continue;
            }

            if (this.isNightShelterWaitPathRetryCoolingDown(pos)) {
                continue;
            }

            availableCarpets.add(pos.immutable());
        }

        List<BlockPos> candidates = availableCarpets.stream()
                .sorted(Comparator.comparingDouble(pos -> pos.distSqr(origin)))
                .toList();

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        for (int batchStart = 0; batchStart < candidates.size(); batchStart += NIGHT_SHELTER_WAIT_CARPET_PATHFIND_BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + NIGHT_SHELTER_WAIT_CARPET_PATHFIND_BATCH_SIZE, candidates.size());
            List<BlockPos> batchCandidates = candidates.subList(batchStart, batchEnd);
            Path path = this.getNavigation().createPath(new LinkedHashSet<>(batchCandidates), 0);
            boolean progressivePath = path != null
                    && !path.canReach()
                    && this.isProgressivePartialNightShelterWaitPath(path);

            if (path != null && (path.canReach() || progressivePath)) {
                this.lastNightShelterWaitPathCanReach = path.canReach();
                this.lastNightShelterWaitPathProgressive = progressivePath;
                return Optional.of(path.getTarget().immutable());
            }
        }

        this.lastNightShelterWaitSearchLocallyBlocked = true;
        this.lastNightShelterWaitPathFailedCandidates.addAll(candidates);
        return Optional.empty();
    }

    private void updateReopenableNightShelterWaitTarget() {
        Optional<BlockPos> waitPosition = this.findReopenableNightShelterWaitPosition();
        if (waitPosition.isEmpty()) {
            return;
        }

        this.nightShelterWaitPosition = waitPosition.get().immutable();
        this.nightShelterWaitReached = false;
    }

    private Optional<BlockPos> findReopenableNightShelterWaitPosition() {
        List<BlockPos> blockedCarpets = this.unreachableNightShelterWaitRetries.entrySet().stream()
                .filter(entry -> entry.getValue().isBlockedForNight())
                .map(Map.Entry::getKey)
                .filter(this::isAvailableNightShelterWaitCarpet)
                .sorted(Comparator.comparingDouble(pos -> pos.distSqr(this.blockPosition())))
                .toList();

        if (blockedCarpets.isEmpty()) {
            return Optional.empty();
        }

        for (int batchStart = 0; batchStart < blockedCarpets.size(); batchStart += NIGHT_SHELTER_WAIT_CARPET_PATHFIND_BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + NIGHT_SHELTER_WAIT_CARPET_PATHFIND_BATCH_SIZE, blockedCarpets.size());
            List<BlockPos> batchCandidates = blockedCarpets.subList(batchStart, batchEnd);
            Path path = this.getNavigation().createPath(new LinkedHashSet<>(batchCandidates), 0);
            boolean progressivePath = path != null
                    && !path.canReach()
                    && this.isProgressivePartialNightShelterWaitPath(path);
            BlockPos target = path == null ? null : path.getTarget();
            if (target == null || !batchCandidates.contains(target)) {
                continue;
            }

            ShelterPathRetry retry = this.unreachableNightShelterWaitRetries.get(target);
            if (retry == null || !retry.isBlockedForNight()) {
                continue;
            }

            if (this.canReopenSoftPathRetry(target, retry, path, progressivePath)) {
                this.unreachableNightShelterWaitRetries.remove(target);
                this.lastNightShelterWaitPathCanReach = path.canReach();
                this.lastNightShelterWaitPathProgressive = progressivePath;
                return Optional.of(target.immutable());
            }
        }

        return Optional.empty();
    }

    private boolean isAvailableNightShelterWaitCarpet(BlockPos pos) {
        return this.level().getBlockState(pos).is(BlockTags.WOOL_CARPETS)
                && this.isBasicFloorNightShelterWaitCarpet(pos)
                && this.hasNightShelterWaitHeadroom(pos)
                && this.isNightShelterWaitInterior(pos)
                && this.hasNightShelterWaitBodySpace(pos)
                && !this.isNightShelterWaitOccupied(pos);
    }

    private boolean isBasicFloorNightShelterWaitCarpet(BlockPos pos) {
        return this.isBasicFloorShelterPosition(pos);
    }

    private boolean hasNightShelterWaitHeadroom(BlockPos pos) {
        return this.level().getBlockState(pos.above()).isAir();
    }

    private boolean isNightShelterWaitInterior(BlockPos pos) {
        return !this.level().canSeeSky(pos.above());
    }

    private boolean hasNightShelterWaitBodySpace(BlockPos pos) {
        return this.level().noCollision(this, this.getNightShelterWaitBox(pos));
    }

    private boolean isNightShelterWaitOccupied(BlockPos pos) {
        return this.getNightShelterWaitOccupant(pos).isPresent();
    }

    private Optional<RibbitEntity> getNightShelterWaitOccupant(BlockPos pos) {
        return this.getNightShelterWaitOccupant(
                pos,
                this.level().getEntitiesOfClass(
                        RibbitEntity.class,
                        new AABB(pos).inflate(
                                NIGHT_SHELTER_WAIT_CARPET_SEARCH_RANGE,
                                BED_HOME_VERTICAL_RANGE,
                                NIGHT_SHELTER_WAIT_CARPET_SEARCH_RANGE),
                        ribbit -> ribbit != this && ribbit.isAlive()));
    }

    private Optional<RibbitEntity> getNightShelterWaitOccupant(BlockPos pos, List<RibbitEntity> candidates) {
        return candidates.stream()
                .filter(ribbit -> ribbit.hasNightShelterWaitClaim(pos)
                        || ribbit.isPhysicallyOccupyingNightShelterWait(pos))
                .min(Comparator.comparingDouble(ribbit -> this.getDistanceToBlockCenterSqr(ribbit, pos)));
    }

    private List<RibbitEntity> getNightShelterWaitOccupantCandidates(BlockPos min, BlockPos max) {
        AABB searchBox = new AABB(
                min.getX(),
                min.getY(),
                min.getZ(),
                max.getX() + 1.0D,
                max.getY() + 1.0D,
                max.getZ() + 1.0D).inflate(1.0D);
        return this.level().getEntitiesOfClass(
                RibbitEntity.class,
                searchBox,
                ribbit -> ribbit != this && ribbit.isAlive());
    }

    private boolean hasNightShelterWaitClaim(BlockPos pos) {
        return this.nightShelterWaitPosition != null && this.nightShelterWaitPosition.equals(pos);
    }

    private boolean isPhysicallyOccupyingNightShelterWait(BlockPos pos) {
        return this.blockPosition().equals(pos) || this.isNearNightShelterWaitOccupancyCenter(pos);
    }

    private boolean isNearNightShelterWaitOccupancyCenter(BlockPos pos) {
        Vec3 restPosition = this.getNightShelterWaitRestPosition(pos);
        double dx = this.getX() - restPosition.x;
        double dz = this.getZ() - restPosition.z;
        double dy = Math.abs(this.getY() - restPosition.y);
        return dx * dx + dz * dz <= Mth.square(NIGHT_SHELTER_WAIT_OCCUPANCY_HORIZONTAL_DISTANCE)
                && dy <= NIGHT_SHELTER_WAIT_OCCUPANCY_VERTICAL_DISTANCE;
    }

    private double getDistanceToBlockCenterSqr(RibbitEntity ribbit, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        return ribbit.distanceToSqr(center.x, center.y, center.z);
    }

    private Vec3 getNightShelterWaitRestPosition(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, this.getSupportSurfaceY(pos), pos.getZ() + 0.5D);
    }

    private AABB getNightShelterWaitBox(BlockPos pos) {
        Vec3 restPosition = this.getNightShelterWaitRestPosition(pos);
        double halfWidth = this.getBbWidth() / 2.0D;
        return new AABB(
                restPosition.x - halfWidth,
                restPosition.y + 0.001D,
                restPosition.z - halfWidth,
                restPosition.x + halfWidth,
                restPosition.y + this.getBbHeight(),
                restPosition.z + halfWidth);
    }

    private void clearNightShelterWait() {
        this.nightShelterWaitPosition = null;
        this.nightShelterWaitReached = false;
        this.lastNightShelterWaitPathCanReach = false;
        this.lastNightShelterWaitPathProgressive = false;
    }

    private void activateLocalNightShelterWait() {
        this.clearNightShelterWait();
        this.clearNightCommunityAnchor();
        this.localNightShelterWaitActive = true;
        this.nextBedHomeSearchTick = this.tickCount + LOCAL_NIGHT_SHELTER_WAIT_RETRY_TICKS;
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.resetHomeRelatedGoals(false);
    }

    private void delayLocalNightShelterWaitRetry() {
        this.clearNightShelterWait();
        this.clearNightCommunityAnchor();
        this.localNightShelterWaitActive = true;
        this.nextBedHomeSearchTick = this.tickCount + LOCAL_NIGHT_SHELTER_WAIT_RETRY_TICKS;
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.resetHomeRelatedGoals(false);
    }

    private void clearLocalNightShelterWait() {
        if (!this.localNightShelterWaitActive) {
            return;
        }

        this.localNightShelterWaitActive = false;
    }

    private boolean recordLocalNightShelterBlockedSearchIfNeeded() {
        if (!this.lastNightShelterWaitSearchLocallyBlocked
                || this.nightCommunityAnchorPosition == null
                || this.lastNightShelterWaitPathFailedCandidates.isEmpty()) {
            this.clearLocalNightShelterBlockedSearch();
            return false;
        }

        BlockPos currentPosition = this.blockPosition();
        if (this.lastLocalNightShelterBlockedPosition != null
                && this.lastLocalNightShelterBlockedPosition.distSqr(currentPosition)
                <= LOCAL_NIGHT_SHELTER_BLOCKED_POSITION_DISTANCE_SQR) {
            this.localNightShelterBlockedCycles++;
        } else {
            this.localNightShelterBlockedCycles = 1;
        }

        this.lastLocalNightShelterBlockedPosition = currentPosition.immutable();
        boolean confirmed = this.localNightShelterBlockedCycles >= LOCAL_NIGHT_SHELTER_BLOCKED_CONFIRMATION_CYCLES;
        return confirmed;
    }

    private void clearLocalNightShelterBlockedSearch() {
        this.lastLocalNightShelterBlockedPosition = null;
        this.localNightShelterBlockedCycles = 0;
        this.lastNightShelterWaitPathFailedCandidates.clear();
    }

    public boolean isLocalNightShelterWaitActive() {
        return this.localNightShelterWaitActive
                && this.isShelterNight()
                && !this.hasUsableHomePosition()
                && !this.isAutonomousAiPaused();
    }

    public boolean isAtShelterTarget(BlockPos shelterPosition) {
        if (this.homePositionSetByPlayer) {
            return shelterPosition.closerToCenterThan(this.position(), 1.8D);
        }

        if (this.isValidBedHomeSlot(shelterPosition)) {
            return this.isCloseEnoughToBedHomeRestPosition(this.getBedHomeRestPosition(shelterPosition))
                    || this.isNearStackedBedHomeApproach(shelterPosition);
        }

        return false;
    }

    public boolean hasPlayerSetHomePosition() {
        return this.homePositionSetByPlayer;
    }

    public boolean hasUsableHomePosition() {
        return this.homePositionSetByPlayer
                || this.hasValidAutomaticBedHome()
                || this.hasValidPendingBedHome();
    }

    public boolean hasActiveNightShelterWait() {
        return this.nightShelterWaitPosition != null;
    }

    public boolean canUseNightShelterWait() {
        return this.isShelterNight()
                && !this.isAutonomousAiPaused()
                && !this.isVehicle()
                && !this.isLeashed()
                && !this.getBuffing()
                && this.canUpdateNightShelterNavigationNow()
                && !this.hasUsableHomePosition()
                && this.hasActiveNightShelterWait()
                && !this.isNightShelterWaitReadyToRetry();
    }

    public boolean isNightShelterWaitReadyToRetry() {
        if (!this.nightShelterWaitReached || this.tickCount < this.nextBedHomeSearchTick) {
            return false;
        }

        return this.canUpdateGroundNavigationNow();
    }

    @Nullable
    public BlockPos getNightShelterWaitPosition() {
        return this.nightShelterWaitPosition;
    }

    public boolean isAtNightShelterWaitPosition() {
        if (this.nightShelterWaitPosition == null) {
            return false;
        }

        return this.nightShelterWaitPosition.closerToCenterThan(this.position(), NIGHT_SHELTER_WAIT_REACHED_DISTANCE);
    }

    public void holdAtNightShelterWaitPosition() {
        if (this.nightShelterWaitPosition == null) {
            return;
        }

        Vec3 restPosition = this.getNightShelterWaitRestPosition(this.nightShelterWaitPosition);
        this.getNavigation().stop();
        if (this.nightShelterWaitReached && this.isNearNightShelterWaitRestPosition(restPosition)) {
            return;
        }

        boolean recentered = this.nightShelterWaitReached;
        this.setDeltaMovement(Vec3.ZERO);
        this.setPos(restPosition.x, restPosition.y, restPosition.z);
        this.nightShelterWaitReached = true;
        this.clearLocalNightShelterBlockedSearch();
    }

    public boolean tryFineApproachToNightShelterWaitPosition(double speedModifier) {
        if (this.nightShelterWaitPosition == null) {
            return false;
        }

        Vec3 restPosition = this.getNightShelterWaitRestPosition(this.nightShelterWaitPosition);
        if (this.isNearNightShelterWaitRestPosition(restPosition)) {
            return false;
        }

        this.getMoveControl().setWantedPosition(restPosition.x, restPosition.y, restPosition.z, speedModifier);
        return true;
    }

    private boolean isNearNightShelterWaitRestPosition(Vec3 restPosition) {
        double dx = this.getX() - restPosition.x;
        double dz = this.getZ() - restPosition.z;
        double dy = Math.abs(this.getY() - restPosition.y);
        return dx * dx + dz * dz <= Mth.square(NIGHT_SHELTER_WAIT_CENTER_HORIZONTAL_DISTANCE)
                && dy <= NIGHT_SHELTER_WAIT_CENTER_VERTICAL_DISTANCE;
    }

    private void updateRestingPoseState() {
        boolean shouldRest = this.shouldUseRestingPose();
        if (this.getResting() && !shouldRest && !this.tryLeaveLowerBunkBeforeStanding()) {
            return;
        }

        if (this.getResting() != shouldRest) {
            this.setResting(shouldRest);
        }
    }

    private boolean shouldUseRestingPose() {
        if (this.isPassenger()) {
            return true;
        }

        if (this.shouldRestWhileLeashed()) {
            return true;
        }

        if (this.getSeatedBuffing()) {
            return true;
        }

        boolean centeredAtNightRestPosition = this.isCenteredAtNightRestPosition();
        if (!this.isShelterNight()
                || this.isVehicle()
                || this.isLeashed()
                || this.isTrading()
                || (!centeredAtNightRestPosition && this.isAirborneForNightShelterActions())
                || (!centeredAtNightRestPosition && !this.getNavigation().isDone())
                || this.getPlayingInstrument()
                || this.getFishing()
                || this.getWatering()
                || this.getBuffing()) {
            return false;
        }

        if (this.isLocalNightShelterWaitActive()) {
            return true;
        }

        return centeredAtNightRestPosition;
    }

    private boolean isCenteredAtNightRestPosition() {
        return this.isCenteredAtAutomaticBedHome()
                || this.isRestingInLowerBunkBed()
                || this.isCenteredAtPlayerSetHomePosition()
                || this.isCenteredAtNightShelterWaitPosition();
    }

    public boolean canCastBuffFromCurrentNightRestingPosition() {
        return this.getResting()
                && !this.isAutonomousAiPaused()
                && !this.isVehicle()
                && !this.isLeashed()
                && !this.isTrading()
                && !this.isAirborneForNightShelterActions()
                && (this.isCenteredAtAutomaticBedHome()
                        || this.isRestingInLowerBunkBed()
                        || this.isCenteredAtPlayerSetHomePosition()
                        || this.isCenteredAtNightShelterWaitPosition());
    }

    public boolean shouldYieldNightShelterWaitForRestingBuff(double triggerRange) {
        return this.getRibbitData().getProfession().equals(RibbitProfessionModule.SORCERER)
                && this.getBuffCooldown() <= 0
                && this.canCastBuffFromCurrentNightRestingPosition()
                && this.hasNearbyNonCombatPlayer(triggerRange);
    }

    public boolean shouldUseSeatedBuffingPose() {
        return this.isShelterNight() && this.getResting() && this.isRestingInLowerBunkBed();
    }

    private boolean hasNearbyNonCombatPlayer(double range) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return false;
        }

        return !serverLevel.getNearbyPlayers(
                TargetingConditions.forNonCombat().range(range),
                this,
                this.getBoundingBox().inflate(range, 5.0D, range)).isEmpty();
    }

    private boolean isCenteredAtAutomaticBedHome() {
        return this.hasValidAutomaticBedHome()
                && this.isCloseEnoughToBedHomeRestPosition(this.getBedHomeRestPosition(this.homePosition));
    }

    private boolean isCenteredAtPlayerSetHomePosition() {
        if (!this.homePositionSetByPlayer || this.homePosition == null) {
            return false;
        }

        Vec3 restPosition = Vec3.atBottomCenterOf(this.homePosition);
        double dx = this.getX() - restPosition.x;
        double dz = this.getZ() - restPosition.z;
        double dy = Math.abs(this.getY() - restPosition.y);
        return dx * dx + dz * dz <= Mth.square(PLAYER_SET_HOME_REST_HORIZONTAL_DISTANCE)
                && dy <= PLAYER_SET_HOME_REST_VERTICAL_DISTANCE;
    }

    private boolean isCenteredAtNightShelterWaitPosition() {
        if (!this.nightShelterWaitReached || this.nightShelterWaitPosition == null) {
            return false;
        }

        return this.isNearNightShelterWaitRestPosition(this.getNightShelterWaitRestPosition(this.nightShelterWaitPosition));
    }

    private boolean tryLeaveLowerBunkBeforeStanding() {
        if (!this.isRestingInLowerBunkBed()) {
            return true;
        }

        BlockState bedState = this.level().getBlockState(this.homePosition);
        if (!bedState.is(BlockTags.BEDS) || !bedState.hasProperty(BedBlock.FACING)) {
            return true;
        }

        Optional<Vec3> standUpPosition = BedBlock.findStandUpPosition(
                this.getType(),
                this.level(),
                this.homePosition,
                bedState.getValue(BedBlock.FACING),
                this.getYRot());
        if (standUpPosition.isEmpty()) {
            return false;
        }

        Vec3 position = standUpPosition.get();
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.setPos(position.x, position.y, position.z);
        return true;
    }

    private boolean isRestingInLowerBunkBed() {
        return this.homePositionIsAutomaticBed
                && this.homePosition != null
                && this.isLowerBunkBedHomeSlot(this.homePosition)
                && this.isCloseEnoughToBedHomeRestPosition(this.getBedHomeRestPosition(this.homePosition));
    }

    public boolean canUpdateNightShelterNavigationNow() {
        return !this.isUmbrellaFalling() && (this.onGround() || this.isInLiquid());
    }

    private boolean canUpdateGroundNavigationNow() {
        return !this.isAutonomousAiPaused() && this.canUpdateNightShelterNavigationNow();
    }

    private boolean isAirborneForNightShelterActions() {
        return this.isUmbrellaFalling()
                || (!this.onGround() && !this.isInLiquid() && this.getDeltaMovement().y < -0.02D);
    }

    public void handleNightShelterWaitPathFailed() {
        if (this.nightShelterWaitPosition != null) {
            this.markNightShelterWaitPathRetry(this.nightShelterWaitPosition);
        }
        this.clearNightShelterWait();
        this.scheduleNextBedHomeSearch();
    }

    @Nullable
    public Path createNightShelterWaitNavigationPath(BlockPos waitPosition) {
        Path path = this.getNavigation().createPath(waitPosition, 0);
        boolean progressivePath = path != null
                && !path.canReach()
                && this.isProgressivePartialNightShelterWaitPath(path);
        if (path == null || (!path.canReach() && !progressivePath)) {
            return null;
        }

        return path;
    }

    public boolean hasBedHomeNavigationTarget() {
        return !this.homePositionSetByPlayer
                && (this.hasValidAutomaticBedHome() || this.hasValidPendingBedHome());
    }

    public boolean clearAutomaticBedHomeIfTooFar() {
        if (this.level().isClientSide()
                || this.homePositionSetByPlayer
                || !this.homePositionIsAutomaticBed
                || this.homePosition == null
                || !this.isAutomaticBedHomeTooFar()) {
            return false;
        }

        this.releaseAutomaticBedHome();
        return true;
    }

    private boolean isAutomaticBedHomeTooFar() {
        if (!this.homePositionIsAutomaticBed || this.homePosition == null) {
            return false;
        }

        double maxDistance = AUTOMATIC_BED_HOME_MAX_DISTANCE;
        return this.homePosition.distSqr(this.blockPosition()) > maxDistance * maxDistance;
    }

    private void releaseAutomaticBedHome() {
        this.homePosition = null;
        this.homePositionIsAutomaticBed = false;
        this.activeBedHomeApproachPosition = null;
        this.scheduleNextBedHomeSearch();
        this.clearAutomaticBedHomePathFailures();
        this.resetHomeRelatedGoals(false);
    }

    public boolean isBedHomeNavigationTarget(@Nullable BlockPos pos) {
        if (pos == null) {
            return false;
        }

        return (this.hasValidAutomaticBedHome() && pos.equals(this.homePosition))
                || (this.hasValidPendingBedHome() && pos.equals(this.pendingBedHomePosition));
    }

    @Nullable
    public BlockPos getShelterNavigationPosition() {
        if (this.hasValidPendingBedHome()) {
            return this.pendingBedHomePosition;
        }

        if (this.homePositionSetByPlayer || this.hasValidAutomaticBedHome()) {
            return this.homePosition;
        }

        return null;
    }

    public BlockPos getDayActivityHomePosition() {
        if (this.homePositionSetByPlayer || this.hasValidAutomaticBedHome()) {
            return this.getHomePosition();
        }

        return this.getFallbackDayActivityHomePosition();
    }

    private void setDayActivityHomePosition(BlockPos pos) {
        this.dayActivityHomePosition = pos.immutable();
    }

    private BlockPos getFallbackDayActivityHomePosition() {
        if (this.dayActivityHomePosition == null) {
            this.setDayActivityHomePosition(this.blockPosition());
        }

        return this.dayActivityHomePosition;
    }

    public boolean isShelterNight() {
        if (this.level().dimensionType().hasFixedTime()) {
            return false;
        }

        return Math.floorMod(this.level().getDefaultClockTime(), VANILLA_DAY_PERIOD_TICKS) >= VANILLA_NIGHT_MARKER_TICK;
    }

    public boolean isDayActivityTime() {
        if (this.level().dimensionType().hasFixedTime()) {
            return false;
        }

        return !this.isShelterNight();
    }

    public BlockPos getStrollAnchorPosition() {
        if (this.isShelterNight()
                && !this.hasUsableHomePosition()
                && this.nightCommunityAnchorPosition != null) {
            return this.nightCommunityAnchorPosition;
        }

        return this.getDayActivityHomePosition();
    }

    public boolean shouldLeaveUpperShelter() {
        if (this.isShelterNight() || this.isAutonomousAiPaused() || this.isVehicle() || this.isLeashed()) {
            return false;
        }

        return this.blockPosition().getY() > this.getDayActivityHomePosition().getY() + 2;
    }

    public boolean isOutdoorActivityPosition() {
        return this.isOutdoorActivityPosition(this.blockPosition());
    }

    public boolean isOutdoorActivityPosition(BlockPos pos) {
        return this.level().canSeeSky(pos) || this.level().canSeeSky(pos.above());
    }

    public boolean isStableOutdoorActivityPosition() {
        return this.isStableOutdoorActivityPosition(this.blockPosition(), this.getOnPos());
    }

    public boolean isStableOutdoorActivityPosition(BlockPos pos) {
        return this.isStableOutdoorActivityPosition(pos, pos.below());
    }

    private boolean isStableOutdoorActivityPosition(BlockPos pos, BlockPos supportPos) {
        return this.isOutdoorActivityPosition(pos) && this.isStableActivitySupport(supportPos);
    }

    private boolean isStableActivitySupport(BlockPos supportPos) {
        BlockState supportState = this.level().getBlockState(supportPos);
        return !supportState.getCollisionShape(this.level(), supportPos).isEmpty()
                && !supportState.is(BlockTags.FENCES)
                && !supportState.is(BlockTags.WALLS)
                && !(supportState.getBlock() instanceof FenceGateBlock);
    }

    private boolean isHorizontallyCloseToShelter(BlockPos shelterPosition, double distance) {
        double x = shelterPosition.getX() + 0.5D - this.getX();
        double z = shelterPosition.getZ() + 0.5D - this.getZ();
        return x * x + z * z <= distance * distance;
    }

    public NavigationBlockerHandling tryHandleNavigationBlocker(
            Vec3 navigationTarget,
            int navigationPriority,
            String context,
            double speed) {
        if (this.level().isClientSide()) {
            return NavigationBlockerHandling.NONE;
        }

        Vec3 toTarget = new Vec3(navigationTarget.x - this.getX(), 0.0D, navigationTarget.z - this.getZ());
        double targetDistanceSqr = toTarget.lengthSqr();
        if (targetDistanceSqr < 0.25D) {
            return NavigationBlockerHandling.NONE;
        }

        Vec3 targetDirection = toTarget.normalize();
        AABB searchBox = this.getBoundingBox().inflate(NAVIGATION_BLOCKER_SEARCH_DISTANCE, 0.15D, NAVIGATION_BLOCKER_SEARCH_DISTANCE);
        Optional<RibbitEntity> blocker = this.level()
                .getEntitiesOfClass(RibbitEntity.class, searchBox, this::isNavigationBlockerCandidate)
                .stream()
                .filter(other -> this.isBlockingNavigationTo(other, targetDirection, navigationPriority))
                .min(Comparator.comparingDouble(other -> other.distanceToSqr(this)));

        if (blocker.isEmpty()) {
            return this.tryDetourAroundBlockingEntity(navigationTarget, targetDirection, context, speed);
        }

        this.getNavigation().stop();
        this.clearNavigationEntityBlockerDetour();
        return NavigationBlockerHandling.YIELDED;
    }

    private boolean isNavigationBlockerCandidate(RibbitEntity other) {
        if (other == this || !other.isAlive() || other.isVehicle() || other.isPassenger()) {
            return false;
        }

        return !other.getNavigation().isDone() || other.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4D;
    }

    private boolean isBlockingNavigationTo(RibbitEntity other, Vec3 targetDirection, int navigationPriority) {
        Vec3 toOther = new Vec3(other.getX() - this.getX(), 0.0D, other.getZ() - this.getZ());
        double otherDistanceSqr = toOther.lengthSqr();
        if (otherDistanceSqr < 1.0E-4D || otherDistanceSqr > Mth.square(NAVIGATION_BLOCKER_SEARCH_DISTANCE)) {
            return false;
        }

        double forwardDot = targetDirection.dot(toOther.normalize());
        if (forwardDot < NAVIGATION_BLOCKER_FORWARD_DOT) {
            return false;
        }

        int otherPriority = other.getNavigationYieldPriority();
        if (otherPriority != navigationPriority) {
            return navigationPriority < otherPriority;
        }

        return this.getUUID().compareTo(other.getUUID()) > 0;
    }

    private NavigationBlockerHandling tryDetourAroundBlockingEntity(
            Vec3 navigationTarget,
            Vec3 targetDirection,
            String context,
            double speed) {
        Optional<Entity> blocker = this.findBlockingNavigationEntity(targetDirection);
        if (blocker.isEmpty()) {
            return NavigationBlockerHandling.NONE;
        }

        Entity blockingEntity = blocker.get();
        BlockPos targetKey = BlockPos.containing(navigationTarget);
        if (!targetKey.equals(this.navigationEntityBlockerTarget)
                || !context.equals(this.navigationEntityBlockerContext)) {
            this.navigationEntityBlockerTarget = targetKey.immutable();
            this.navigationEntityBlockerContext = context;
            this.navigationEntityBlockerDetourCycles = 0;
        }

        this.navigationEntityBlockerDetourCycles++;
        if (this.navigationEntityBlockerDetourCycles > NAVIGATION_ENTITY_BLOCKER_MAX_DETOURS) {
            this.clearNavigationEntityBlockerDetour();
            return NavigationBlockerHandling.EXHAUSTED;
        }

        Vec3 detourPosition = this.getNavigationEntityBlockerDetourPosition(blockingEntity);
        if (detourPosition == null) {
            return NavigationBlockerHandling.EXHAUSTED;
        }

        this.getNavigation().stop();
        boolean moving = this.getNavigation().moveTo(detourPosition.x, detourPosition.y, detourPosition.z, speed);
        if (!moving) {
            return NavigationBlockerHandling.EXHAUSTED;
        }

        this.navigationEntityBlockerDetourUntilTick = this.tickCount + NAVIGATION_ENTITY_BLOCKER_DETOUR_TICKS;
        return NavigationBlockerHandling.DETOURING;
    }

    private Optional<Entity> findBlockingNavigationEntity(Vec3 targetDirection) {
        AABB searchBox = this.getBoundingBox().inflate(NAVIGATION_BLOCKER_SEARCH_DISTANCE, 0.25D, NAVIGATION_BLOCKER_SEARCH_DISTANCE);
        return this.level()
                .getEntities(this, searchBox, this::isNavigationEntityBlockerCandidate)
                .stream()
                .filter(entity -> this.isBlockingNavigationTo(entity, targetDirection))
                .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(this)));
    }

    private boolean isNavigationEntityBlockerCandidate(Entity entity) {
        if (entity == this
                || entity.isRemoved()
                || entity.isSpectator()
                || entity.isPassenger()
                || entity instanceof RibbitEntity) {
            return false;
        }

        return EntitySelector.CAN_BE_COLLIDED_WITH.test(entity)
                || this.canCollideWith(entity)
                || entity.canCollideWith(this);
    }

    private boolean isBlockingNavigationTo(Entity entity, Vec3 targetDirection) {
        AABB contactBox = this.getBoundingBox().inflate(
                NAVIGATION_ENTITY_BLOCKER_CONTACT_MARGIN,
                NAVIGATION_ENTITY_BLOCKER_VERTICAL_MARGIN,
                NAVIGATION_ENTITY_BLOCKER_CONTACT_MARGIN);
        if (contactBox.intersects(entity.getBoundingBox())) {
            return true;
        }

        AABB forwardContactBox = this.getBoundingBox()
                .expandTowards(
                        targetDirection.x * NAVIGATION_ENTITY_BLOCKER_AHEAD_DISTANCE,
                        0.0D,
                        targetDirection.z * NAVIGATION_ENTITY_BLOCKER_AHEAD_DISTANCE)
                .inflate(
                        NAVIGATION_ENTITY_BLOCKER_CONTACT_MARGIN,
                        NAVIGATION_ENTITY_BLOCKER_VERTICAL_MARGIN,
                        NAVIGATION_ENTITY_BLOCKER_CONTACT_MARGIN);
        if (forwardContactBox.intersects(entity.getBoundingBox())) {
            return true;
        }

        Vec3 entityCenter = entity.getBoundingBox().getCenter();
        Vec3 toEntity = new Vec3(entityCenter.x - this.getX(), 0.0D, entityCenter.z - this.getZ());
        double entityDistanceSqr = toEntity.lengthSqr();
        if (entityDistanceSqr < 1.0E-4D || entityDistanceSqr > Mth.square(NAVIGATION_BLOCKER_SEARCH_DISTANCE)) {
            return false;
        }

        double forwardDot = targetDirection.dot(toEntity.normalize());
        return forwardDot >= NAVIGATION_BLOCKER_FORWARD_DOT;
    }

    @Nullable
    private Vec3 getNavigationEntityBlockerDetourPosition(Entity blocker) {
        Vec3 detourPosition = LandRandomPos.getPosAway(
                this,
                NAVIGATION_ENTITY_BLOCKER_RANDOM_RANGE,
                NAVIGATION_ENTITY_BLOCKER_RANDOM_VERTICAL_RANGE,
                blocker.position());
        if (detourPosition != null) {
            return detourPosition;
        }

        return DefaultRandomPos.getPos(
                this,
                NAVIGATION_ENTITY_BLOCKER_RANDOM_RANGE,
                NAVIGATION_ENTITY_BLOCKER_RANDOM_VERTICAL_RANGE);
    }

    public boolean tickNavigationEntityBlockerDetour(double speed) {
        if (this.navigationEntityBlockerDetourUntilTick <= this.tickCount) {
            if (this.navigationEntityBlockerDetourUntilTick > 0) {
                this.clearNavigationEntityBlockerDetour();
            }

            return false;
        }

        this.getNavigation().setSpeedModifier(speed);
        return true;
    }

    public int getNavigationEntityBlockerDetourUntilTick() {
        return this.navigationEntityBlockerDetourUntilTick;
    }

    public void clearNavigationEntityBlockerDetour() {
        this.navigationEntityBlockerDetourUntilTick = 0;
    }

    public void clearNavigationEntityBlockerMemory() {
        this.clearNavigationEntityBlockerDetour();
        this.navigationEntityBlockerTarget = null;
        this.navigationEntityBlockerContext = null;
        this.navigationEntityBlockerDetourCycles = 0;
    }

    private int getNavigationYieldPriority() {
        if (this.hasUsableHomePosition()) {
            return 2;
        }

        if (this.hasActiveNightShelterWait()) {
            return 1;
        }

        return 0;
    }

    public Vec3 getHomeNavigationTarget(BlockPos homePosition) {
        if (!this.homePositionSetByPlayer && this.isValidBedHomeSlot(homePosition)) {
            BlockPos approachPosition = this.getPreferredBedHomeApproachPosition(homePosition);
            if (approachPosition != null) {
                return Vec3.atBottomCenterOf(approachPosition);
            }
        }

        return Vec3.atBottomCenterOf(homePosition);
    }

    @Nullable
    public Path createShelterNavigationPath(BlockPos homePosition) {
        if (!this.homePositionSetByPlayer && this.isValidBedHomeSlot(homePosition)) {
            Set<BlockPos> approachPositions = this.getValidBedHomeApproachPositions(homePosition);
            BlockPos preferredApproachPosition = this.getPreferredBedHomeApproachPosition(homePosition);
            if (preferredApproachPosition != null) {
                approachPositions = Set.of(preferredApproachPosition);
            }

            if (!approachPositions.isEmpty()) {
                Path path = this.getNavigation().createPath(approachPositions, 0);
                this.resolveBedHomeApproachFromPath(homePosition, approachPositions, path)
                        .ifPresent(approachPosition -> this.activeBedHomeApproachPosition = approachPosition);
                if (path != null && !path.canReach()
                        && !this.isAcceptablePartialBedHomePath(path)
                        && !this.isProgressivePartialBedHomePath(path)) {
                    return null;
                }
                return path;
            }

            return this.getNavigation().createPath(Set.of(this.getBedHomePoiPos(homePosition)), 1);
        }

        return this.getNavigation().createPath(homePosition, 0);
    }

    @Nullable
    private BlockPos getPreferredBedHomeApproachPosition(BlockPos homePosition) {
        if (homePosition.equals(this.pendingBedHomePosition)
                && this.pendingBedHomeApproachPosition != null
                && this.getBedHomeApproachPositions(homePosition).contains(this.pendingBedHomeApproachPosition)
                && this.hasBedHomeApproachSpace(this.pendingBedHomeApproachPosition, homePosition)) {
            return this.pendingBedHomeApproachPosition;
        }

        if (this.activeBedHomeApproachPosition != null
                && this.getBedHomeApproachPositions(homePosition).contains(this.activeBedHomeApproachPosition)
                && this.hasBedHomeApproachSpace(this.activeBedHomeApproachPosition, homePosition)) {
            return this.activeBedHomeApproachPosition;
        }

        return null;
    }

    private Optional<BlockPos> resolveBedHomeApproachFromPath(
            BlockPos bedSlot,
            Set<BlockPos> approachPositions,
            @Nullable Path path) {
        if (path == null) {
            return Optional.empty();
        }

        BlockPos target = path.getTarget();
        if (approachPositions.contains(target) && this.hasBedHomeApproachSpace(target, bedSlot)) {
            return Optional.of(target.immutable());
        }

        BlockPos reference = path.getEndNode() == null ? target : path.getEndNode().asBlockPos();
        return approachPositions.stream()
                .filter(approachPosition -> this.hasBedHomeApproachSpace(approachPosition, bedSlot))
                .filter(approachPosition -> this.isBedHomeApproachCloseToPathEnd(approachPosition, reference))
                .min(Comparator.comparingDouble(approachPosition -> approachPosition.distSqr(reference)))
                .map(BlockPos::immutable);
    }

    public void handleShelterPathFailed(BlockPos homePosition) {
        if (this.level().isClientSide() || this.homePositionSetByPlayer) {
            return;
        }

        boolean blockedForNight = this.markBedHomePathRetry(homePosition);

        if (homePosition.equals(this.pendingBedHomePosition)) {
            if (!blockedForNight) {
                return;
            }

            this.pendingBedHomePosition = null;
            this.pendingBedHomeApproachPosition = null;
            this.activeBedHomeApproachPosition = null;
            this.scheduleNextBedHomeSearch();
        } else if (this.homePositionIsAutomaticBed && homePosition.equals(this.homePosition)) {
            this.trackAutomaticBedHomePathFailure(homePosition);
        }
    }

    public void handleShelterPathStarted(BlockPos homePosition) {
        if (this.homePositionIsAutomaticBed && homePosition.equals(this.homePosition)) {
            this.clearAutomaticBedHomePathFailures();
        }
    }

    private void trackAutomaticBedHomePathFailure(BlockPos homePosition) {
        if (!homePosition.equals(this.failingAutomaticBedHomePosition)) {
            this.failingAutomaticBedHomePosition = homePosition.immutable();
            this.automaticBedHomePathFailures = 0;
        }

        this.automaticBedHomePathFailures++;
        int maxFailures = this.getAutomaticBedHomeMaxPathFailures(homePosition);
        if (this.automaticBedHomePathFailures < maxFailures) {
            return;
        }

        this.homePosition = null;
        this.homePositionIsAutomaticBed = false;
        this.activeBedHomeApproachPosition = null;
        this.scheduleNextBedHomeSearch();
        this.clearAutomaticBedHomePathFailures();
        this.resetHomeRelatedGoals(false);
    }

    private int getAutomaticBedHomeMaxPathFailures(BlockPos homePosition) {
        return this.isUpperBunkBedHomeSlot(homePosition)
                ? BED_HOME_UPPER_BUNK_MAX_PATH_FAILURES
                : BED_HOME_MAX_PATH_FAILURES;
    }

    private void clearAutomaticBedHomePathFailures() {
        this.failingAutomaticBedHomePosition = null;
        this.automaticBedHomePathFailures = 0;
    }

    public boolean tryRestAtHomeBed() {
        if (this.level().isClientSide()
                || this.homePositionSetByPlayer
                || !this.isShelterNight()
                || this.isLeashed()
                || this.isAutonomousAiPaused()
                || this.isVehicle()
                || !this.canUpdateNightShelterNavigationNow()
                || !this.hasBedHomeNavigationTarget()) {
            return false;
        }

        BlockPos home = this.getShelterNavigationPosition();
        if (home == null || !this.isUsableAutomaticBedHomeSlot(home)) {
            return false;
        }

        boolean reachedRestPosition = this.isCloseEnoughToBedHomeRestPosition(this.getBedHomeRestPosition(home));
        boolean reachedBedHomeApproach = !reachedRestPosition && this.isNearBedHomeSlotApproach(home);
        boolean reachedStackedBedApproach = !reachedRestPosition
                && !reachedBedHomeApproach
                && this.isNearStackedBedHomeApproach(home);
        boolean pendingBedHome = home.equals(this.pendingBedHomePosition);
        if (!reachedRestPosition && !reachedBedHomeApproach && !reachedStackedBedApproach) {
            return false;
        }

        this.holdAtBedHome(home);
        if (pendingBedHome) {
            this.setAutomaticBedHome(home);
        }
        return true;
    }

    private BedHomeSearchResult findNearbyBedHomeSlot() {
        return this.findNearbyBedHomeSlot(true);
    }

    private BedHomeSearchResult findNearbyBedHomeSlot(boolean markUnreachableCandidates) {
        Set<BlockPos> bedSlots = new LinkedHashSet<>();
        if (this.level() instanceof ServerLevel serverLevel) {
            PoiManager poiManager = serverLevel.getPoiManager();
            poiManager.findAllClosestFirstWithType(
                            poiType -> poiType.is(PoiTypes.HOME),
                            poiPos -> poiPos.closerThan(this.blockPosition(), BED_HOME_SEARCH_RANGE),
                            this.blockPosition(),
                            BED_HOME_SEARCH_RANGE,
                            PoiManager.Occupancy.ANY)
                    .forEach(pair -> {
                        this.getBedHomeSlotFromPoi(pair.getSecond(), true).ifPresent(bedSlots::add);
                        this.getBedHomeSlotFromPoi(pair.getSecond(), false).ifPresent(bedSlots::add);
                    });
        }

        List<BlockPos> sortedBedSlots = new ArrayList<>(bedSlots);
        BlockPos origin = this.blockPosition();
        sortedBedSlots.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));
        return this.findNearbyBedHomeSlot(sortedBedSlots, markUnreachableCandidates);
    }

    private BedHomeSearchResult findNearbyBedHomeSlot(List<BlockPos> bedSlots, boolean markUnreachableCandidates) {
        int pendingClaims = 0;
        List<BlockPos> pathCandidates = new ArrayList<>(BED_HOME_PATHFIND_BATCH_SIZE);

        this.clearExpiredBedHomePathRetries();
        for (BlockPos bedSlot : bedSlots) {
            if (!this.hasBedHomeBodySpace(bedSlot)) {
                continue;
            }

            if (!this.isBasicFloorBedHomeSlot(bedSlot)) {
                continue;
            }

            Optional<BedHomeSlotClaim> claim = this.getBedHomeSlotClaim(bedSlot);
            if (claim.isPresent()) {
                if (claim.get().pendingHome()) {
                    pendingClaims++;
                }
                continue;
            }

            if (this.isBedHomePathRetryCoolingDown(bedSlot)) {
                continue;
            }

            if (pathCandidates.size() >= BED_HOME_PATHFIND_BATCH_SIZE) {
                continue;
            }

            pathCandidates.add(bedSlot);
        }

        Optional<BedHomePathSelection> reachableSlot = this.findReachableBedHomeSlot(pathCandidates);
        if (reachableSlot.isPresent()) {
            return new BedHomeSearchResult(reachableSlot, pendingClaims);
        }

        if (markUnreachableCandidates) {
            pathCandidates.forEach(this::markBedHomePathRetry);
        }

        return new BedHomeSearchResult(Optional.empty(), pendingClaims);
    }

    private Optional<BedHomePathSelection> findReachableBedHomeSlot(List<BlockPos> bedSlots) {
        if (bedSlots.isEmpty()) {
            return Optional.empty();
        }

        Map<BlockPos, BlockPos> slotByApproach = new LinkedHashMap<>();
        for (BlockPos bedSlot : bedSlots) {
            for (BlockPos approachPosition : this.getValidBedHomeApproachPositions(bedSlot)) {
                slotByApproach.putIfAbsent(approachPosition, bedSlot);
            }
        }

        if (slotByApproach.isEmpty()) {
            return Optional.empty();
        }

        Path path = this.getNavigation().createPath(slotByApproach.keySet(), 0);
        if (path == null) {
            return Optional.empty();
        }

        if (!path.canReach()) {
            boolean progressivePartialPath = this.isProgressivePartialBedHomePath(path);
            if (!this.isAcceptablePartialBedHomePath(path) && !progressivePartialPath) {
                return Optional.empty();
            }

        }

        return this.resolveBedHomePathSelection(path, slotByApproach);
    }

    private Optional<BedHomePathSelection> findReopenableBlockedBedHomeSlot() {
        List<BlockPos> blockedSlots = this.unreachableBedHomeRetries.entrySet().stream()
                .filter(entry -> entry.getValue().isBlockedForNight())
                .map(Map.Entry::getKey)
                .filter(this::isReopenableBlockedBedHomeSlot)
                .sorted(Comparator.comparingDouble(pos -> pos.distSqr(this.blockPosition())))
                .toList();

        if (blockedSlots.isEmpty()) {
            return Optional.empty();
        }

        for (int batchStart = 0; batchStart < blockedSlots.size(); batchStart += BED_HOME_PATHFIND_BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + BED_HOME_PATHFIND_BATCH_SIZE, blockedSlots.size());
            List<BlockPos> batchSlots = blockedSlots.subList(batchStart, batchEnd);
            Optional<BedHomePathSelection> selection = this.findReopenableBlockedBedHomeSlotBatch(batchSlots);
            if (selection.isPresent()) {
                BedHomePathSelection bedHome = selection.get();
                this.unreachableBedHomeRetries.remove(bedHome.slot());
                return selection;
            }
        }

        return Optional.empty();
    }

    private boolean isReopenableBlockedBedHomeSlot(BlockPos bedSlot) {
        return this.isBedHomeSlot(bedSlot)
                && this.hasBedHomeBodySpace(bedSlot)
                && this.isBasicFloorBedHomeSlot(bedSlot)
                && this.getBedHomeSlotClaim(bedSlot).isEmpty();
    }

    private Optional<BedHomePathSelection> findReopenableBlockedBedHomeSlotBatch(List<BlockPos> bedSlots) {
        Map<BlockPos, BlockPos> slotByApproach = new LinkedHashMap<>();
        for (BlockPos bedSlot : bedSlots) {
            for (BlockPos approachPosition : this.getValidBedHomeApproachPositions(bedSlot)) {
                slotByApproach.putIfAbsent(approachPosition, bedSlot);
            }
        }

        if (slotByApproach.isEmpty()) {
            return Optional.empty();
        }

        Path path = this.getNavigation().createPath(slotByApproach.keySet(), 0);
        if (path == null) {
            return Optional.empty();
        }

        boolean progressivePath = !path.canReach() && this.isProgressivePartialBedHomePath(path);
        Optional<BedHomePathSelection> selection = this.resolveBedHomePathSelection(path, slotByApproach);
        if (selection.isEmpty()) {
            return Optional.empty();
        }

        BedHomePathSelection bedHome = selection.get();
        ShelterPathRetry retry = this.unreachableBedHomeRetries.get(bedHome.slot());
        if (retry == null || !retry.isBlockedForNight()) {
            return Optional.empty();
        }

        if (this.canReopenSoftPathRetry(bedHome.slot(), retry, path, progressivePath)) {
            return selection;
        }

        return Optional.empty();
    }

    private Optional<BedHomePathSelection> resolveBedHomePathSelection(
            Path path,
            Map<BlockPos, BlockPos> slotByApproach) {
        BlockPos target = path.getTarget();
        BlockPos matchingSlot = slotByApproach.get(target);
        if (matchingSlot != null && this.hasBedHomeApproachSpace(target, matchingSlot)) {
            return Optional.of(new BedHomePathSelection(matchingSlot, target.immutable()));
        }

        BlockPos reference = path.getEndNode() == null ? target : path.getEndNode().asBlockPos();
        return slotByApproach.entrySet().stream()
                .filter(entry -> this.hasBedHomeApproachSpace(entry.getKey(), entry.getValue()))
                .filter(entry -> this.isBedHomeApproachCloseToPathEnd(entry.getKey(), reference))
                .min(Comparator.comparingDouble(entry -> entry.getKey().distSqr(reference)))
                .map(entry -> new BedHomePathSelection(entry.getValue(), entry.getKey()));
    }

    private boolean isAcceptablePartialBedHomePath(Path path) {
        Node endNode = path.getEndNode();
        if (endNode == null) {
            return false;
        }

        BlockPos target = path.getTarget();
        BlockPos endPos = endNode.asBlockPos();
        int verticalGap = Math.abs(target.getY() - endPos.getY());
        double horizontalDistanceSqr = Mth.square(target.getX() - endPos.getX())
                + Mth.square(target.getZ() - endPos.getZ());
        if (verticalGap <= 2
                && horizontalDistanceSqr <= Mth.square(BED_HOME_PARTIAL_PATH_MAX_HORIZONTAL_DISTANCE)) {
            return true;
        }

        return false;
    }

    private boolean isProgressivePartialBedHomePath(Path path) {
        if (path.canReach()) {
            return true;
        }

        Node endNode = path.getEndNode();
        if (endNode == null || path.getTarget() == null) {
            return false;
        }

        BlockPos target = path.getTarget();
        double startDistanceSqr = this.blockPosition().distSqr(target);
        double endDistanceSqr = endNode.asBlockPos().distSqr(target);
        return endDistanceSqr < startDistanceSqr - SHELTER_PATH_PROGRESS_DISTANCE_SQR;
    }

    private boolean isProgressivePartialNightShelterWaitPath(Path path) {
        if (path.canReach()) {
            return true;
        }

        Node endNode = path.getEndNode();
        if (endNode == null || path.getTarget() == null) {
            return false;
        }

        BlockPos target = path.getTarget();
        double startDistanceSqr = this.blockPosition().distSqr(target);
        double endDistanceSqr = endNode.asBlockPos().distSqr(target);
        return endDistanceSqr < startDistanceSqr - SHELTER_PATH_PROGRESS_DISTANCE_SQR;
    }

    private boolean isProgressivePartialNightCommunityAnchorPath(Path path) {
        if (path.canReach()) {
            return true;
        }

        Node endNode = path.getEndNode();
        if (endNode == null || path.getTarget() == null) {
            return false;
        }

        BlockPos target = path.getTarget();
        double startDistanceSqr = this.blockPosition().distSqr(target);
        double endDistanceSqr = endNode.asBlockPos().distSqr(target);
        return endDistanceSqr < startDistanceSqr - SHELTER_PATH_PROGRESS_DISTANCE_SQR;
    }

    private boolean canReopenSoftPathRetry(
            BlockPos retryTarget,
            ShelterPathRetry retry,
            Path path,
            boolean progressivePath) {
        Node endNode = path.getEndNode();
        double endDistanceSqr = endNode == null ? Double.MAX_VALUE : retryTarget.distSqr(endNode.asBlockPos());
        boolean canReach = path.canReach();
        boolean improvesBlockedProgress = !canReach
                && progressivePath
                && retry.isPathProgressImproved(endDistanceSqr);
        return canReach || improvesBlockedProgress;
    }

    private BlockPos getBedHomePoiPos(BlockPos bedSlot) {
        BlockState blockState = this.level().getBlockState(bedSlot);
        if (!blockState.is(BlockTags.BEDS) || !blockState.hasProperty(BedBlock.PART)) {
            return bedSlot.immutable();
        }

        if (blockState.getValue(BedBlock.PART) == BedPart.HEAD) {
            return bedSlot.immutable();
        }

        return bedSlot.relative(blockState.getValue(BedBlock.FACING)).immutable();
    }

    private Set<BlockPos> getBedHomeApproachPositions(BlockPos bedSlot) {
        if (this.isStackedBedHomeSlot(bedSlot)) {
            return this.getBunkBedHomeSnapApproachPositions(bedSlot);
        }

        return this.getBedHomeSlotAdjacentApproachPositions(bedSlot);
    }

    private Set<BlockPos> getValidBedHomeApproachPositions(BlockPos bedSlot) {
        Set<BlockPos> approachPositions = new LinkedHashSet<>();
        for (BlockPos approachPosition : this.getBedHomeApproachPositions(bedSlot)) {
            if (this.hasBedHomeApproachSpace(approachPosition, bedSlot)) {
                approachPositions.add(approachPosition);
            }
        }

        return approachPositions;
    }

    private Set<BlockPos> getRawBedHomeApproachPositions(BlockPos bedSlot) {
        Set<BlockPos> approachPositions = new LinkedHashSet<>();
        if (!this.isBedHomeSlot(bedSlot)) {
            return approachPositions;
        }

        BlockState bedState = this.level().getBlockState(bedSlot);
        Direction bedFacing = bedState.getValue(BedBlock.FACING);
        BlockPos headPos = bedState.getValue(BedBlock.PART) == BedPart.HEAD
                ? bedSlot
                : bedSlot.relative(bedFacing);
        BlockPos footPos = headPos.relative(bedFacing.getOpposite());

        Direction left = bedFacing.getCounterClockWise();
        Direction right = bedFacing.getClockWise();
        approachPositions.add(bedSlot.relative(left).immutable());
        approachPositions.add(bedSlot.relative(right).immutable());
        approachPositions.add(headPos.relative(left).immutable());
        approachPositions.add(headPos.relative(right).immutable());
        approachPositions.add(footPos.relative(left).immutable());
        approachPositions.add(footPos.relative(right).immutable());
        if (this.isStackedBedHomeSlot(bedSlot)) {
            return approachPositions;
        }

        approachPositions.add(bedSlot.relative(bedFacing).immutable());
        approachPositions.add(bedSlot.relative(bedFacing.getOpposite()).immutable());
        approachPositions.add(headPos.relative(bedFacing).immutable());
        approachPositions.add(footPos.relative(bedFacing.getOpposite()).immutable());
        return approachPositions;
    }

    private boolean hasBedHomeApproachSpace(BlockPos pos) {
        return this.level().noCollision(this, this.getBedHomeApproachBox(pos));
    }

    private boolean hasBedHomeApproachSpace(BlockPos pos, BlockPos bedSlot) {
        return this.level().noCollision(this, this.getBedHomeApproachBox(pos, bedSlot));
    }

    private boolean isBedHomeApproachCloseToPathEnd(BlockPos approachPosition, BlockPos pathEndPosition) {
        double horizontalDistanceSqr = Mth.square(approachPosition.getX() - pathEndPosition.getX())
                + Mth.square(approachPosition.getZ() - pathEndPosition.getZ());
        return horizontalDistanceSqr <= Mth.square(BED_HOME_PARTIAL_PATH_MAX_HORIZONTAL_DISTANCE)
                && Math.abs(approachPosition.getY() - pathEndPosition.getY()) <= 2;
    }

    private AABB getBedHomeApproachBox(BlockPos pos) {
        return this.getBedHomeApproachBox(pos, null);
    }

    private AABB getBedHomeApproachBox(BlockPos pos, @Nullable BlockPos bedSlot) {
        EntityDimensions dimensions = this.getStandingDimensions();
        double halfWidth = dimensions.width() / 2.0D;
        double minY = this.getBedHomeApproachBoxMinY(pos, bedSlot);
        return new AABB(
                pos.getX() + 0.5D - halfWidth,
                minY,
                pos.getZ() + 0.5D - halfWidth,
                pos.getX() + 0.5D + halfWidth,
                minY + dimensions.height(),
                pos.getZ() + 0.5D + halfWidth);
    }

    private double getBedHomeApproachBoxMinY(BlockPos pos, @Nullable BlockPos bedSlot) {
        if (bedSlot != null
                && this.isLowerBunkBedHomeSlot(bedSlot)
                && this.isLowWalkableBedHomeApproachSurface(pos)) {
            return this.getSupportSurfaceY(pos) + 0.001D;
        }

        return pos.getY() + 0.001D;
    }

    private boolean isLowWalkableBedHomeApproachSurface(BlockPos pos) {
        BlockState state = this.level().getBlockState(pos);
        var collisionShape = state.getCollisionShape(this.level(), pos);
        if (collisionShape.isEmpty()) {
            return false;
        }

        double collisionTop = collisionShape.max(Direction.Axis.Y);
        return collisionTop > 0.0D && collisionTop <= this.maxUpStep();
    }

    private boolean isBedHomePathRetryCoolingDown(BlockPos bedSlot) {
        return this.isShelterPathRetryCoolingDown(this.unreachableBedHomeRetries, bedSlot);
    }

    private boolean markBedHomePathRetry(BlockPos bedSlot) {
        ShelterPathRetry retry = this.getOrCreateShelterPathRetry(this.unreachableBedHomeRetries, bedSlot);
        boolean blockedForNight = retry.markAttempt(
                this.tickCount,
                bedSlot.distSqr(this.blockPosition()),
                BED_HOME_MAX_PATH_FAILURES);
        return blockedForNight;
    }

    private void clearExpiredBedHomePathRetries() {
        this.clearExpiredShelterPathRetries(this.unreachableBedHomeRetries);
    }

    private boolean isNightShelterWaitPathRetryCoolingDown(BlockPos waitPosition) {
        return this.isShelterPathRetryCoolingDown(this.unreachableNightShelterWaitRetries, waitPosition);
    }

    private boolean markNightShelterWaitPathRetry(BlockPos waitPosition) {
        ShelterPathRetry retry = this.getOrCreateShelterPathRetry(
                this.unreachableNightShelterWaitRetries,
                waitPosition);
        boolean blockedForNight = retry.markAttempt(
                this.tickCount,
                waitPosition.distSqr(this.blockPosition()),
                NIGHT_SHELTER_WAIT_MAX_PATH_FAILURES);
        return blockedForNight;
    }

    private void blockLastNightShelterWaitPathFailedCandidatesForNight() {
        if (this.lastNightShelterWaitPathFailedCandidates.isEmpty()) {
            return;
        }

        for (BlockPos waitPosition : this.lastNightShelterWaitPathFailedCandidates) {
            ShelterPathRetry retry = this.getOrCreateShelterPathRetry(
                    this.unreachableNightShelterWaitRetries,
                    waitPosition);
            if (retry.isBlockedForNight()) {
                continue;
            }

            retry.blockForNight(this.tickCount, waitPosition.distSqr(this.blockPosition()));
        }
    }

    private void clearExpiredNightShelterWaitPathRetries() {
        this.clearExpiredShelterPathRetries(this.unreachableNightShelterWaitRetries);
    }

    private boolean isShelterPathRetryCoolingDown(Map<BlockPos, ShelterPathRetry> retries, BlockPos target) {
        ShelterPathRetry retry = retries.get(target);
        if (retry == null) {
            return false;
        }

        if (retry.isBlockedForNight()) {
            return true;
        }

        if (!retry.isStillValid(this.tickCount)) {
            retries.remove(target);
            return false;
        }

        return !retry.shouldRetry(this.tickCount);
    }

    private ShelterPathRetry getOrCreateShelterPathRetry(Map<BlockPos, ShelterPathRetry> retries, BlockPos target) {
        BlockPos immutableTarget = target.immutable();
        ShelterPathRetry retry = retries.get(immutableTarget);
        if (retry == null || (!retry.isBlockedForNight() && !retry.isStillValid(this.tickCount))) {
            retry = new ShelterPathRetry(this.random, this.tickCount);
            retries.put(immutableTarget, retry);
        }

        return retry;
    }

    private void clearExpiredShelterPathRetries(Map<BlockPos, ShelterPathRetry> retries) {
        retries.entrySet().removeIf(entry ->
                !entry.getValue().isBlockedForNight() && !entry.getValue().isStillValid(this.tickCount));
    }

    private Optional<BlockPos> getBedHomeSlotFromPoi(BlockPos headPos, boolean headSlot) {
        if (!this.isBedHomeSlot(headPos) || !this.isBedHeadSlot(headPos)) {
            return Optional.empty();
        }

        if (headSlot) {
            return Optional.of(headPos.immutable());
        }

        BlockState headState = this.level().getBlockState(headPos);
        BlockPos footPos = headPos.relative(headState.getValue(BedBlock.FACING).getOpposite());
        return this.isBedHomeSlot(footPos) && !this.isBedHeadSlot(footPos)
                ? Optional.of(footPos.immutable())
                : Optional.empty();
    }

    private boolean hasValidAutomaticBedHome() {
        BlockPos home = this.getHomePosition();
        return this.isAutomaticBedHomeRaw()
                && !this.isAutomaticBedHomeTooFar()
                && !this.hasHigherPriorityCommittedBedHomeClaim(home);
    }

    private boolean hasCommittedShelterHome() {
        return this.homePositionSetByPlayer || this.hasValidAutomaticBedHome();
    }

    private BlockPos getCommittedShelterHomePosition() {
        return this.getHomePosition();
    }

    private boolean isRestingAtAutomaticBedHome() {
        return this.hasValidAutomaticBedHome() && this.isAtShelterTarget(this.homePosition);
    }

    private boolean hasValidPendingBedHome() {
        return this.pendingBedHomePosition != null
                && this.pendingBedHomeApproachPosition != null
                && this.isUsableAutomaticBedHomeSlot(this.pendingBedHomePosition)
                && this.getBedHomeApproachPositions(this.pendingBedHomePosition).contains(this.pendingBedHomeApproachPosition)
                && this.hasBedHomeApproachSpace(this.pendingBedHomeApproachPosition, this.pendingBedHomePosition)
                && !this.isBedHomeSlotClaimed(this.pendingBedHomePosition);
    }

    private boolean isAutomaticBedHomeRaw() {
        return !this.homePositionSetByPlayer
                && this.homePositionIsAutomaticBed
                && this.homePosition != null
                && this.isUsableAutomaticBedHomeSlot(this.homePosition);
    }

    private void tickFloatingPlantNavigationAssist() {
        if (this.getNavigation().isDone()) {
            return;
        }

        BlockPos floatingPlantPos = this.getOnPos();
        if (!this.isFloatingPlantNavigationBlock(this.level().getBlockState(floatingPlantPos))
                || !this.level().getFluidState(floatingPlantPos.below()).is(FluidTags.WATER)) {
            return;
        }

        Path path = this.getNavigation().getPath();
        if (path == null || path.isDone()) {
            return;
        }

        this.advanceFloatingPlantPathNode(path);
        if (path.isDone()) {
            return;
        }

        Vec3 target = this.getFloatingPlantSurfaceNavigationTarget(path, floatingPlantPos);
        Vec3 horizontal = new Vec3(target.x - this.getX(), 0.0D, target.z - this.getZ());
        if (horizontal.lengthSqr() < 1.0E-4D) {
            return;
        }

        double speedModifier = this.isInWater() ? WATER_SPEED_MULTIPLIER : 1.0D;
        this.getMoveControl().setWantedPosition(target.x, target.y, target.z, speedModifier);
    }

    private void advanceFloatingPlantPathNode(Path path) {
        int advances = 0;
        while (!path.isDone()
                && advances < 2
                && this.isHorizontallyCloseToPathTarget(path.getNextEntityPos(this), FLOATING_PLANT_NAVIGATION_NODE_REACHED_DISTANCE)) {
            path.advance();
            advances++;
        }
    }

    private Vec3 getFloatingPlantSurfaceNavigationTarget(Path path, BlockPos floatingPlantPos) {
        Vec3 target = path.getNextEntityPos(this);
        if (this.isHorizontallyCloseToPathTarget(target, 0.2D) && path.getNextNodeIndex() + 1 < path.getNodeCount()) {
            target = path.getEntityPosAtNode(this, path.getNextNodeIndex() + 1);
        }

        double plantSurfaceY = floatingPlantPos.getY()
                + this.level().getBlockState(floatingPlantPos).getCollisionShape(this.level(), floatingPlantPos).max(Direction.Axis.Y);
        double surfaceY = Math.max(this.getY(), plantSurfaceY);
        if (target.y < surfaceY) {
            return new Vec3(target.x, surfaceY, target.z);
        }

        return target;
    }

    private boolean isHorizontallyCloseToPathTarget(Vec3 target, double distance) {
        double x = target.x - this.getX();
        double z = target.z - this.getZ();
        return x * x + z * z <= distance * distance;
    }

    private boolean isFloatingPlantNavigationBlock(BlockState state) {
        return state.is(Blocks.LILY_PAD) || state.is(BlockModule.GIANT_LILYPAD.get());
    }

    private boolean isValidBedHomeSlot(BlockPos pos) {
        return this.isBedHomeSlot(pos) && this.hasBedHomeBodySpace(pos);
    }

    private boolean isUsableAutomaticBedHomeSlot(BlockPos pos) {
        return this.isValidBedHomeSlot(pos);
    }

    private boolean isBedHomeSlot(BlockPos pos) {
        BlockState blockState = this.level().getBlockState(pos);
        return blockState.is(BlockTags.BEDS) && blockState.hasProperty(BedBlock.PART);
    }

    private boolean isLowerBunkBedHomeSlot(BlockPos pos) {
        return this.isBedHomeSlot(pos) && this.isBedHomeSlot(pos.above());
    }

    private boolean isUpperBunkBedHomeSlot(BlockPos pos) {
        return this.isBedHomeSlot(pos) && this.isBedHomeSlot(pos.below());
    }

    private boolean isStackedBedHomeSlot(BlockPos pos) {
        return this.isLowerBunkBedHomeSlot(pos) || this.isUpperBunkBedHomeSlot(pos);
    }

    private boolean isBedHeadSlot(BlockPos pos) {
        return this.level().getBlockState(pos).getValue(BedBlock.PART) == BedPart.HEAD;
    }

    private boolean hasBedHomeBodySpace(BlockPos pos) {
        return this.level().noCollision(this, this.getBedHomeBodyBox(pos));
    }

    private boolean isBasicFloorBedHomeSlot(BlockPos pos) {
        return this.isBasicFloorShelterPosition(pos);
    }

    private boolean isBasicFloorShelterPosition(BlockPos pos) {
        return Math.abs(pos.getY() - this.blockPosition().getY()) <= BED_HOME_BASIC_FLOOR_VERTICAL_DISTANCE;
    }

    private AABB getBedHomeBodyBox(BlockPos pos) {
        Vec3 restPosition = this.getBedHomeRestPosition(pos);
        EntityDimensions dimensions = this.getBedHomeBodyDimensions(pos);
        double halfWidth = dimensions.width() / 2.0D;
        return new AABB(
                restPosition.x - halfWidth,
                restPosition.y + 0.001D,
                restPosition.z - halfWidth,
                restPosition.x + halfWidth,
                restPosition.y + dimensions.height(),
                restPosition.z + halfWidth);
    }

    private EntityDimensions getBedHomeBodyDimensions(BlockPos pos) {
        return this.isLowerBunkBedHomeSlot(pos)
                ? this.getRestingDimensions()
                : this.getStandingDimensions();
    }

    private EntityDimensions getRestingDimensions() {
        return RESTING_DIMENSIONS.scale(this.getAgeScale()).scale(this.getScale());
    }

    private EntityDimensions getStandingDimensions() {
        return this.getType().getDimensions().scale(this.getAgeScale()).scale(this.getScale());
    }

    private Vec3 getBedHomeRestPosition(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, this.getSupportSurfaceY(pos), pos.getZ() + 0.5D);
    }

    private double getSupportSurfaceY(BlockPos pos) {
        BlockState blockState = this.level().getBlockState(pos);
        double supportTop = blockState.getCollisionShape(this.level(), pos).max(Direction.Axis.Y);
        if (supportTop <= 0.0D) {
            supportTop = 0.5625D;
        }

        return pos.getY() + supportTop;
    }

    private boolean isCloseEnoughToBedHomeRestPosition(Vec3 restPosition) {
        return this.isNearBedHomeRestPosition(
                restPosition,
                BED_HOME_SNAP_HORIZONTAL_DISTANCE,
                BED_HOME_SNAP_VERTICAL_DISTANCE);
    }

    private boolean isNearBedHomeSlotApproach(BlockPos bedSlot) {
        if (this.isStackedBedHomeSlot(bedSlot)) {
            return false;
        }

        return this.getBedHomeSlotAdjacentApproachPositions(bedSlot).stream()
                .filter(this::hasBedHomeApproachSpace)
                .anyMatch(pos -> this.isNearBlockCenter(
                        pos,
                        BED_HOME_UPPER_BUNK_APPROACH_HORIZONTAL_DISTANCE,
                        BED_HOME_UPPER_BUNK_APPROACH_VERTICAL_DISTANCE));
    }

    private boolean isNearStackedBedHomeApproach(BlockPos bedSlot) {
        if (!this.isStackedBedHomeSlot(bedSlot)) {
            return false;
        }

        return this.getBunkBedHomeSnapApproachPositions(bedSlot).stream()
                .anyMatch(pos -> this.isNearBlockCenter(
                        pos,
                        BED_HOME_UPPER_BUNK_APPROACH_HORIZONTAL_DISTANCE,
                        BED_HOME_UPPER_BUNK_APPROACH_VERTICAL_DISTANCE));
    }

    private Set<BlockPos> getBunkBedHomeSnapApproachPositions(BlockPos bedSlot) {
        Set<BlockPos> approachPositions = new LinkedHashSet<>();
        if (!this.isBedHomeSlot(bedSlot)) {
            return approachPositions;
        }

        Direction bedFacing = this.level().getBlockState(bedSlot).getValue(BedBlock.FACING);
        approachPositions.add(bedSlot.relative(bedFacing.getCounterClockWise()).immutable());
        approachPositions.add(bedSlot.relative(bedFacing.getClockWise()).immutable());
        return approachPositions;
    }

    private Set<BlockPos> getBedHomeSlotAdjacentApproachPositions(BlockPos bedSlot) {
        Set<BlockPos> approachPositions = new LinkedHashSet<>();
        if (!this.isBedHomeSlot(bedSlot)) {
            return approachPositions;
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            approachPositions.add(bedSlot.relative(direction).immutable());
        }

        return approachPositions;
    }

    private boolean isNearBlockCenter(BlockPos pos, double horizontalDistance, double verticalDistance) {
        double dx = this.getX() - (pos.getX() + 0.5D);
        double dz = this.getZ() - (pos.getZ() + 0.5D);
        double dy = Math.abs(this.getY() - pos.getY());
        return dx * dx + dz * dz <= horizontalDistance * horizontalDistance && dy <= verticalDistance;
    }

    private boolean isNearBedHomeRestPosition(Vec3 restPosition, double horizontalDistance, double verticalDistance) {
        double dx = this.getX() - restPosition.x;
        double dz = this.getZ() - restPosition.z;
        double dy = Math.abs(this.getY() - restPosition.y);
        return dx * dx + dz * dz <= horizontalDistance * horizontalDistance && dy <= verticalDistance;
    }

    private void holdAtBedHome(BlockPos home) {
        Vec3 restPosition = this.getBedHomeRestPosition(home);
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        if (!this.getResting()) {
            this.setResting(true);
        }
        this.setPos(restPosition.x, restPosition.y, restPosition.z);
    }

    private void tickDoorNavigationAssist() {
        Path path = this.getNavigation().getPath();
        if (path == null || path.notStarted() || path.isDone()) {
            this.lastCheckedDoorNode = null;
            this.remainingDoorNodeCooldown = 0;
            return;
        }

        Node nextNode = path.getNextNode();
        if (nextNode.equals(this.lastCheckedDoorNode)) {
            this.remainingDoorNodeCooldown = DOOR_INTERACT_NODE_COOLDOWN_TICKS;
        } else if (this.remainingDoorNodeCooldown-- > 0) {
            return;
        }

        this.lastCheckedDoorNode = nextNode;
        Node previousNode = path.getPreviousNode();
        this.openShelterDoorAtPathNode(previousNode);
        this.openShelterDoorAtPathNode(nextNode);
        this.closeShelterDoorsThatHaveBeenPassedThrough(previousNode, nextNode);
    }

    private void openShelterDoorAtPathNode(@Nullable Node node) {
        if (node == null) {
            return;
        }

        BlockPos pos = node.asBlockPos();
        this.setShelterDoorOpen(pos, true);
    }

    private void tickShelterDoors() {
        if (this.tickCount % 20 != 0 || this.shelterDoorsToClose.isEmpty()) {
            return;
        }

        Path path = this.getNavigation().getPath();
        Node previousNode = path == null || path.isDone() ? null : path.getPreviousNode();
        Node nextNode = path == null || path.isDone() ? null : path.getNextNode();
        this.closeShelterDoorsThatHaveBeenPassedThrough(previousNode, nextNode);
    }

    private void closeShelterDoorsThatHaveBeenPassedThrough(@Nullable Node movingFromNode, @Nullable Node movingToNode) {
        this.shelterDoorsToClose.removeIf(pos -> {
            if (this.isShelterDoorPathNode(pos, movingFromNode) || this.isShelterDoorPathNode(pos, movingToNode)) {
                return false;
            }

            if (this.isShelterDoorTooFarAway(pos)) {
                return true;
            }

            BlockState state = this.level().getBlockState(pos);
            if (!this.isMobInteractableDoor(state)) {
                return true;
            }

            DoorBlock door = (DoorBlock) state.getBlock();
            if (!door.isOpen(state)) {
                return true;
            }

            if (this.areOtherRibbitsComingThroughDoor(pos)) {
                return true;
            }

            door.setOpen(this, this.level(), state, pos, false);
            return true;
        });
    }

    private void tickBasicBedHomeState() {
        if (!this.isShelterNight()) {
            this.pendingBedHomePosition = null;
            this.pendingBedHomeApproachPosition = null;
            this.activeBedHomeApproachPosition = null;
            this.unreachableBedHomeRetries.clear();
            this.unreachableNightShelterWaitRetries.clear();
            this.clearAutomaticBedHomePathFailures();
            this.clearNightCommunityAnchor();
            this.clearNightShelterWait();
            this.clearLocalNightShelterWait();
            this.clearLocalNightShelterBlockedSearch();
            this.initialBedHomeSearchScheduled = false;
            return;
        }

        if (!this.homePositionSetByPlayer && this.homePositionIsAutomaticBed && !this.hasValidAutomaticBedHome()) {
            this.homePosition = null;
            this.homePositionIsAutomaticBed = false;
            this.activeBedHomeApproachPosition = null;
            this.nextBedHomeSearchTick = this.tickCount;
        }
    }

    private boolean areOtherRibbitsComingThroughDoor(BlockPos doorPos) {
        return !this.level().getEntitiesOfClass(
                RibbitEntity.class,
                new AABB(doorPos).inflate(DOOR_HOLD_FOR_OTHER_RIBBITS_DISTANCE),
                ribbit -> ribbit != this
                        && ribbit.isAlive()
                        && doorPos.closerToCenterThan(ribbit.position(), DOOR_HOLD_FOR_OTHER_RIBBITS_DISTANCE)
                        && ribbit.isPathingThroughShelterDoor(doorPos)).isEmpty();
    }

    private boolean isPathingThroughShelterDoor(BlockPos doorPos) {
        Path path = this.getNavigation().getPath();
        if (path == null || path.isDone()) {
            return false;
        }

        return this.isShelterDoorPathNode(doorPos, path.getPreviousNode())
                || this.isShelterDoorPathNode(doorPos, path.getNextNode());
    }

    private boolean isShelterDoorPathNode(BlockPos doorPos, @Nullable Node node) {
        return node != null && this.isShelterDoorPathNode(doorPos, node.asBlockPos());
    }

    private boolean isShelterDoorPathNode(BlockPos doorPos, BlockPos nodePos) {
        BlockPos nodeDoorPos = this.getMobInteractableDoorPos(nodePos);
        return nodeDoorPos != null && nodeDoorPos.equals(doorPos);
    }

    private void setShelterDoorOpen(BlockPos pos, boolean open) {
        BlockPos doorPos = this.getMobInteractableDoorPos(pos);
        if (doorPos == null) {
            return;
        }

        BlockState doorState = this.level().getBlockState(doorPos);
        if (!this.isMobInteractableDoor(doorState)) {
            return;
        }

        DoorBlock doorBlock = (DoorBlock) doorState.getBlock();

        if (doorState.getValue(DoorBlock.OPEN) == open) {
            if (open) {
                this.shelterDoorsToClose.add(doorPos.immutable());
            }
            return;
        }

        doorBlock.setOpen(this, this.level(), doorState, doorPos, open);

        if (open) {
            this.shelterDoorsToClose.add(doorPos.immutable());
        }
    }

    @Nullable
    private BlockPos getMobInteractableDoorPos(BlockPos pos) {
        BlockState state = this.level().getBlockState(pos);
        if (this.isMobInteractableDoor(state)) {
            return this.getLowerShelterDoorPos(pos, state);
        }

        return null;
    }

    private boolean isMobInteractableDoor(BlockState state) {
        return state.is(BlockTags.MOB_INTERACTABLE_DOORS, holder -> holder.getBlock() instanceof DoorBlock);
    }

    private BlockPos getLowerShelterDoorPos(BlockPos pos, BlockState blockState) {
        return blockState.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
                ? pos.below()
                : pos.immutable();
    }

    private boolean isShelterDoorTooFarAway(BlockPos pos) {
        return !pos.closerToCenterThan(this.position(), DOOR_CLOSE_FORGET_DISTANCE);
    }

    private boolean isBedHomeSlotClaimed(BlockPos pos) {
        return this.getBedHomeSlotClaim(pos).isPresent();
    }

    private boolean hasHigherPriorityCommittedBedHomeClaim(BlockPos pos) {
        AABB searchBox = new AABB(pos).inflate(BED_HOME_SEARCH_RANGE, BED_HOME_VERTICAL_RANGE, BED_HOME_SEARCH_RANGE);
        return !this.level().getEntitiesOfClass(
                RibbitEntity.class,
                searchBox,
                ribbit -> ribbit != this
                        && ribbit.isAlive()
                        && ribbit.isAutomaticBedHomeRaw()
                        && pos.equals(ribbit.homePosition)
                        && ribbit.getUUID().compareTo(this.getUUID()) < 0)
                .isEmpty();
    }

    private Optional<BedHomeSlotClaim> getBedHomeSlotClaim(BlockPos pos) {
        AABB searchBox = new AABB(pos).inflate(BED_HOME_SEARCH_RANGE, BED_HOME_VERTICAL_RANGE, BED_HOME_SEARCH_RANGE);
        return this.level().getEntitiesOfClass(
                RibbitEntity.class,
                searchBox,
                ribbit -> ribbit != this
                        && ribbit.isAlive()
                        && ((ribbit.isAutomaticBedHomeRaw() && pos.equals(ribbit.homePosition))
                        || (ribbit.pendingBedHomePosition != null
                        && pos.equals(ribbit.pendingBedHomePosition)
                        && ribbit.hasValidPendingBedHome())))
                .stream()
                .findFirst()
                .map(ribbit -> new BedHomeSlotClaim(ribbit.isAutomaticBedHomeRaw() && pos.equals(ribbit.homePosition)));
    }

    private record BedHomePathSelection(BlockPos slot, BlockPos approach) {
    }

    private record BedHomeSearchResult(Optional<BedHomePathSelection> selectedHome, int pendingClaims) {
    }

    public enum NavigationBlockerHandling {
        NONE,
        YIELDED,
        DETOURING,
        EXHAUSTED
    }

    private static class ShelterPathRetry {
        private final RandomSource random;
        private int previousAttemptTick;
        private int nextScheduledAttemptTick;
        private int currentDelay;
        private int failuresWithoutProgress;
        private double bestDistanceSqr = Double.MAX_VALUE;
        private boolean blockedForNight;

        private ShelterPathRetry(RandomSource random, int tickCount) {
            this.random = random;
            this.previousAttemptTick = tickCount;
        }

        private boolean markAttempt(int tickCount, double distanceSqr, int maxFailures) {
            this.previousAttemptTick = tickCount;
            if (distanceSqr < this.bestDistanceSqr - SHELTER_PATH_PROGRESS_DISTANCE_SQR) {
                this.bestDistanceSqr = distanceSqr;
                this.failuresWithoutProgress = 0;
                this.currentDelay = 0;
            } else {
                this.failuresWithoutProgress++;
            }

            int delay = this.currentDelay
                    + this.random.nextInt(SHELTER_PATH_RETRY_JITTER_TICKS)
                    + SHELTER_PATH_RETRY_MIN_TICKS;
            this.currentDelay = Math.min(delay, SHELTER_PATH_RETRY_MAX_TICKS);
            this.nextScheduledAttemptTick = tickCount + this.currentDelay;
            if (this.failuresWithoutProgress >= maxFailures) {
                this.blockedForNight = true;
            }

            return this.blockedForNight;
        }

        private boolean isStillValid(int tickCount) {
            return tickCount - this.previousAttemptTick < SHELTER_PATH_RETRY_MAX_TICKS;
        }

        private boolean shouldRetry(int tickCount) {
            return tickCount >= this.nextScheduledAttemptTick;
        }

        private void blockForNight(int tickCount, double distanceSqr) {
            this.previousAttemptTick = tickCount;
            this.nextScheduledAttemptTick = Integer.MAX_VALUE;
            if (distanceSqr < this.bestDistanceSqr) {
                this.bestDistanceSqr = distanceSqr;
            }
            this.blockedForNight = true;
        }

        private int nextScheduledAttemptTick() {
            return this.nextScheduledAttemptTick;
        }

        private int failuresWithoutProgress() {
            return this.failuresWithoutProgress;
        }

        private boolean isBlockedForNight() {
            return this.blockedForNight;
        }

        private boolean isPathProgressImproved(double distanceSqr) {
            return distanceSqr < this.bestDistanceSqr - SHELTER_PATH_PROGRESS_DISTANCE_SQR;
        }

        private double bestDistanceSqr() {
            return this.bestDistanceSqr;
        }
    }

    private record BedHomeSlotClaim(boolean committedHome) {
        private boolean pendingHome() {
            return !this.committedHome;
        }
    }

    public void reassessGoals() {
        if (this.level().isClientSide()) {
            return;
        }

        this.goalSelector.removeGoal(this.musicGoal);
        this.goalSelector.removeGoal(this.prideParadeGoal);
        this.goalSelector.removeGoal(this.waterCropsGoal);
        this.goalSelector.removeGoal(this.fishGoal);
        this.goalSelector.removeGoal(this.applyBuffGoal);
        this.goalSelector.addGoal(5, this.prideParadeGoal);

        if (this.getRibbitData().getProfession().equals(RibbitProfessionModule.NITWIT)) {
            this.goalSelector.addGoal(6, this.musicGoal);
        } else if (this.getRibbitData().getProfession().equals(RibbitProfessionModule.GARDENER)) {
            this.goalSelector.addGoal(6, this.waterCropsGoal);
        } else if (this.getRibbitData().getProfession().equals(RibbitProfessionModule.FISHERMAN)) {
            this.goalSelector.addGoal(4, this.fishGoal);
        } else if (this.getRibbitData().getProfession().equals(RibbitProfessionModule.SORCERER)) {
            this.goalSelector.addGoal(6, this.applyBuffGoal);
        }
    }

    @Override
    public float getSpeed() {
        return super.getSpeed();
    }

    @Override
    public void handleEntityEvent(byte flag) {
        if (flag == 12) {
            this.addParticlesAroundSelf(ParticleTypes.HEART);
        }

        super.handleEntityEvent(flag);
    }

    protected void addParticlesAroundSelf(ParticleOptions particleOptions) {
        for (int i = 0; i < 5; ++i) {
            double d = this.random.nextGaussian() * 0.02;
            double e = this.random.nextGaussian() * 0.02;
            double f = this.random.nextGaussian() * 0.02;
            this.level().addParticle(particleOptions, this.getRandomX(1.0), this.getRandomY() + 1.0, this.getRandomZ(1.0), d, e, f);
        }
    }

    @Override
    public int getMaxHeadXRot() {
        return 0;
    }

    @Override
    public int getMaxHeadYRot() {
        return 0;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob parent) {
        return null;
    }

    public void setInstrument(RibbitInstrument instrument) {
        this.getRibbitData().setInstrument(instrument);
        this.entityData.set(RIBBIT_DATA, this.getRibbitData(), true);
    }

    public int getBuffCooldown() {
        return this.buffCooldown;
    }

    public void setBuffCooldown(int cooldown) {
        this.buffCooldown = cooldown;
    }

    public int getWaterCropsCooldown() {
        return this.waterCropsCooldown;
    }

    public void setWaterCropsCooldown(int cooldown) {
        this.waterCropsCooldown = cooldown;
    }

    public RibbitData getRibbitData() {
        return this.sidedRibbitData;
    }

    public void setRibbitData(RibbitData data) {
        this.sidedRibbitData = data;
        this.entityData.set(RIBBIT_DATA, data);

        if (!this.level().isClientSide()) {
            this.reassessGoals();
        }
    }

    public BlockPos getHomePosition() {
        return this.homePosition == null ? this.blockPosition() : this.homePosition;
    }

    public boolean getPlayingInstrument() {
        return this.isPlayingInstrument;
    }

    public void setPlayingInstrument(boolean playingInstrument) {
        this.entityData.set(PLAYING_INSTRUMENT, playingInstrument);
    }

    public boolean isUmbrellaFalling() {
        return this.isUmbrellaFalling;
    }

    public void setUmbrellaFalling(boolean umbrellaFalling) {
        this.entityData.set(UMBRELLA_FALLING, umbrellaFalling);
    }

    public boolean getWatering() {
        return this.isWatering;
    }

    public void setWatering(boolean isWatering) {
        this.entityData.set(WATERING, isWatering);
    }

    public boolean getFishing() {
        return this.isFishing;
    }

    public void setFishing(boolean isFishing) {
        this.entityData.set(FISHING, isFishing);
    }

    public boolean getResting() {
        return this.isResting;
    }

    public void setResting(boolean isResting) {
        if (this.isResting == isResting) {
            return;
        }

        this.isResting = isResting;
        this.entityData.set(RESTING, isResting);
        this.refreshDimensions();
    }

    public boolean getBuffing() {
        return this.isBuffing;
    }

    public void setBuffing(boolean isBuffing) {
        if (!isBuffing) {
            this.setSeatedBuffing(false);
        }
        this.entityData.set(BUFFING, isBuffing);
    }

    public boolean getSeatedBuffing() {
        return this.isSeatedBuffing;
    }

    public void setSeatedBuffing(boolean seatedBuffing) {
        if (this.isSeatedBuffing == seatedBuffing) {
            return;
        }

        this.isSeatedBuffing = seatedBuffing;
        this.entityData.set(SEATED_BUFFING, seatedBuffing);
    }

    public int getTicksPlayingMusic() {
        return this.ticksPlayingMusic;
    }

    public void setTicksPlayingMusic(int ticksPlayingMusic) {
        this.ticksPlayingMusic = ticksPlayingMusic;
    }

    public Set<RibbitEntity> getRibbitsPlayingMusic() {
        return ribbitsPlayingMusic;
    }

    public void setRibbitsPlayingMusic(Set<RibbitEntity> ribbitsPlayingMusic) {
        this.ribbitsPlayingMusic = new HashSet<>(ribbitsPlayingMusic);
    }

    public void addRibbitToPlayingMusic(RibbitEntity ribbit) {
        this.ribbitsPlayingMusic.add(ribbit);
    }

    public void removeRibbitFromPlayingMusic(RibbitEntity ribbit) {
        this.ribbitsPlayingMusic.remove(ribbit);
    }

    public Set<Player> getPlayersHearingMusic() {
        return this.playersHearingMusic;
    }

    public void setPlayersHearingMusic(Set<Player> playersHearingMusic) {
        this.playersHearingMusic = new HashSet<>(playersHearingMusic);
    }

    @Nullable
    public RibbitEntity getMasterRibbit() {
        return this.masterRibbit;
    }

    public void setMasterRibbit(RibbitEntity masterRibbit) {
        this.masterRibbit = masterRibbit;
    }

    public boolean isMasterRibbit() {
        return this.equals(this.getMasterRibbit());
    }

    public void findNewMasterRibbit() {
        RibbitEntity newMaster = this.getRibbitsPlayingMusic().stream().filter(ribbit -> ribbit != this).findAny().orElse(null);

        if (newMaster != null) {
            for (RibbitEntity ribbit : this.getRibbitsPlayingMusic()) {
                ribbit.setMasterRibbit(newMaster);
            }

            this.getRibbitsPlayingMusic().remove(this);
            this.removeBandMember(this.getRibbitData().getInstrument());

            newMaster.setRibbitsPlayingMusic(this.getRibbitsPlayingMusic());
            newMaster.setPlayersHearingMusic(this.getPlayersHearingMusic());
            newMaster.setTicksPlayingMusic(this.getTicksPlayingMusic());
            newMaster.setBandMembers(this.getBandMembers());
        }

        this.getRibbitsPlayingMusic().clear();
        this.getPlayersHearingMusic().clear();
        this.setTicksPlayingMusic(0);
        this.clearBandMembers();
    }

    public boolean isBandFull() {
        return this.bandMembers.size() == RibbitInstrumentModule.getNumInstruments();
    }

    public void addBandMember(RibbitInstrument instrument) {
        this.bandMembers.add(instrument);
    }

    public void removeBandMember(RibbitInstrument instrument) {
        this.bandMembers.remove(instrument);
    }

    public void clearBandMembers() {
        this.bandMembers.clear();
    }

    public Set<RibbitInstrument> getBandMembers() {
        return this.bandMembers;
    }

    public void setBandMembers(Set<RibbitInstrument> bandMembers) {
        this.bandMembers = new HashSet<>(bandMembers);
    }

    @Override
    public void remove(RemovalReason reason) {
        RibbitPrideParadeGoal.stopParadeFor(this);

        if (this.isMasterRibbit()) {
            findNewMasterRibbit();
        } else if (this.isPlayingInstrument && this.getMasterRibbit() != null) {
            this.getMasterRibbit().getRibbitsPlayingMusic().remove(this);
            this.getMasterRibbit().removeBandMember(this.getRibbitData().getInstrument());
        }

        super.remove(reason);
    }

    public static AttributeSupplier.Builder createRibbitAttributes() {
        return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 15.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.125D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.STEP_HEIGHT, DEFAULT_STEP_HEIGHT);
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundModule.ENTITY_RIBBIT_AMBIENT.get();
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource $$0) {
        return SoundModule.ENTITY_RIBBIT_HURT.get();
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return SoundModule.ENTITY_RIBBIT_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockstate) {
        super.playStepSound(pos, blockstate);
        this.playSound(SoundModule.ENTITY_RIBBIT_STEP.get(), 1.0F, 1.0F);
    }

    public boolean isPrideRibbit() {
        if (ConfigModule.getConfig().general.disablePrideFlagCN && GeoIP.isInChina()) return false;
        Random rand = new Random(this.getUUID().getLeastSignificantBits());

        return isPrideMonth() && this.getRibbitData().getProfession().equals(RibbitProfessionModule.NITWIT) && rand.nextFloat() < 0.33f;
    }

    private static boolean isPrideMonth() {
        if (ConfigModule.getConfig() != null && ConfigModule.getConfig().general.prideFlagAllYear) return true;

        LocalDate date = LocalDate.now();
        var month = date.getMonth();
        return month == Month.JUNE;
    }

    public boolean isInRain() {
        BlockPos pos = this.blockPosition();
        return this.level().isRainingAt(pos) || this.level().isRainingAt(BlockPos.containing(pos.getX(), this.getBoundingBox().maxY, pos.getZ()));
    }

    @Override
    protected @NotNull PathNavigation createNavigation(@NotNull Level level) {
        return new RibbitPathNavigation(this, level);
    }

    private static class RibbitPathNavigation extends GroundPathNavigation {
        private RibbitPathNavigation(Mob mob, Level level) {
            super(mob, level);
        }

        @Override
        protected @NotNull PathFinder createPathFinder(int maxVisitedNodes) {
            this.nodeEvaluator = new RibbitWalkNodeEvaluator();

            this.nodeEvaluator.setCanPassDoors(true);
            this.nodeEvaluator.setCanOpenDoors(true);
            this.nodeEvaluator.setCanFloat(true);

            return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
        }

        private static class RibbitWalkNodeEvaluator extends WalkNodeEvaluator {
            @Override
            protected boolean isAmphibious() {
                return true;
            }
        }

        @Override
        protected boolean hasValidPathType(@NotNull PathType pathType) {
            if (pathType == PathType.WATER || pathType == PathType.WATER_BORDER) {
                return true;
            }

            return super.hasValidPathType(pathType);
        }

        @Override
        public boolean isStableDestination(@NotNull BlockPos pos) {
            return this.level.getFluidState(pos).is(FluidTags.WATER) || super.isStableDestination(pos);
        }

        @Override
        public boolean canCutCorner(@NotNull PathType pathType) {
            return pathType != PathType.WATER
                    && pathType != PathType.WATER_BORDER
                    && super.canCutCorner(pathType);
        }
    }

    private <E extends GeoAnimatable> PlayState predicate(AnimationTest<E> state) {
        AnimationController<E> controller = state.controller();

        if (this.isUmbrellaFalling()) {
            controller.setAnimation(
                    this.getRibbitData().getProfession() == RibbitProfessionModule.FISHERMAN ? FALLING_FISHERMAN : FALLING
            );
        } else if (getBuffing() && this.getSeatedBuffing()) {
            controller.setAnimation(this.isInRain() ? SORCERER_SEATED_BUFF_HOLDING : SORCERER_SEATED_BUFF);
        } else if (getResting()) {
            controller.setAnimation(this.getRestAnimation());
        } else if (getPlayingInstrument() && this.getRibbitData().getInstrument() != RibbitInstrumentModule.NONE) {
            controller.setAnimation(RawAnimation.begin().thenPlay(this.getRibbitData().getInstrument().animationName()));
        } else if (getBuffing()) {
            controller.setAnimation(this.isInRain() ? SORCERER_BUFF_HOLDING : SORCERER_BUFF);
        } else if (getFishing()) {
            controller.setAnimation(this.isInRain() ? FISH_HOLDING : FISH);
        } else if (getWatering()) {
            controller.setAnimation(this.isInRain() ? WATER_CROPS_HOLDING : WATER_CROPS);
        } else if (state.isMoving() && !this.isInWater()) {
            controller.setAnimation(this.getWalkAnimation());
        } else {
            controller.setAnimation(this.getIdleAnimation());
        }

        return PlayState.CONTINUE;
    }

    private RawAnimation getRestAnimation() {
        boolean isRaining = this.isInRain();

        if (this.getRibbitData().getProfession().equals(RibbitProfessionModule.FISHERMAN)) {
            return isRaining ? REST_FISHERMAN_HOLDING : REST_FISHERMAN;
        }

        if (!isRaining && this.isPrideRibbit()) {
            return REST_PRIDE_HOLDING;
        }

        return isRaining ? REST_HOLDING : REST;
    }

    private RawAnimation getWalkAnimation() {
        if (this.getRibbitData().getProfession().equals(RibbitProfessionModule.FISHERMAN)) {
            return this.isInRain() ? WALK_HOLDING_FISHERMAN : WALK_HOLDING_2;
        } else if (this.isPrideRibbit()) {
            return WALK_HOLDING_2;
        } else if (this.getRibbitData().getProfession().equals(RibbitProfessionModule.SORCERER) || this.getRibbitData().getProfession().equals(RibbitProfessionModule.GARDENER)) {
            return this.isInRain() ? WALK_HOLDING_HAT : WALK;
        } else {
            return this.isInRain() ? WALK_HOLDING_1 : WALK;
        }
    }

    private RawAnimation getIdleAnimation() {
        if (this.getRibbitData().getProfession().equals(RibbitProfessionModule.FISHERMAN)) {
            return this.isInRain() ? IDLE_HOLDING_FISHERMAN : IDLE_HOLDING_2;
        } else if (this.isPrideRibbit()) {
            return IDLE_HOLDING_2;
        } else if (this.getRibbitData().getProfession().equals(RibbitProfessionModule.SORCERER) || this.getRibbitData().getProfession().equals(RibbitProfessionModule.GARDENER)) {
            return this.isInRain() ? IDLE_HOLDING_HAT : IDLE;
        } else {
            return this.isInRain() ? IDLE_HOLDING_1 : IDLE;
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<RibbitEntity>("controller", 5, this::predicate));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Nullable
    @Override
    public Player getTradingPlayer() {
        return this.tradingPlayer;
    }

    @Override
    public MerchantOffers getOffers() {
        if (this.offers == null) {
            this.offers = new MerchantOffers();
            RibbitTradeModule.updateTrades(this);
        }

        return this.offers;
    }

    @Override
    public void overrideOffers(MerchantOffers merchantOffers) {
    }

    @Override
    public void notifyTrade(MerchantOffer merchantOffer) {
        merchantOffer.increaseUses();
        this.ambientSoundTime = -this.getAmbientSoundInterval();
        this.rewardTradeXp(merchantOffer);
    }

    protected void rewardTradeXp(MerchantOffer merchantOffer) {
        int i = 3 + this.random.nextInt(4);

        if (merchantOffer.shouldRewardExp()) {
            this.level().addFreshEntity(new ExperienceOrb(this.level(), this.getX(), this.getY() + 0.5, this.getZ(), i));
        }
    }

    @Override
    public void notifyTradeUpdated(ItemStack itemStack) {
        if (!this.level().isClientSide() && this.ambientSoundTime > -this.getAmbientSoundInterval() + 20) {
            this.ambientSoundTime = -this.getAmbientSoundInterval();
        }
    }

    private void startTrading(Player player) {
        this.getNavigation().stop();
        this.getLookControl().setLookAt(player, 30.0F, (float) this.getMaxHeadXRot());
        this.setTradingPlayer(player);
        this.openTradingScreen(player, this.getDisplayName(), 0);
    }

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        boolean bl = this.getTradingPlayer() != null && player == null;
        this.tradingPlayer = player;

        if (bl) {
            this.stopTrading();
        }
    }

    protected void stopTrading() {
        this.setTradingPlayer(null);
        this.resetSpecialPrices();
    }

    private void resetSpecialPrices() {
        for (MerchantOffer merchantOffer : this.getOffers()) {
            merchantOffer.resetSpecialPriceDiff();
        }
    }

    @Override
    public boolean canRestock() {
        return true;
    }

    public void restock() {
        this.updateDemand();
        for (MerchantOffer merchantOffer : this.getOffers()) {
            merchantOffer.resetUses();
        }
        this.resendOffersToTradingPlayer();
        this.lastRestockGameTime = this.level().getGameTime();
        ++this.numberOfRestocksToday;
    }

    private void resendOffersToTradingPlayer() {
        MerchantOffers merchantOffers = this.getOffers();
        Player player = this.getTradingPlayer();
        if (player != null && !merchantOffers.isEmpty()) {
            player.sendMerchantOffers(player.containerMenu.containerId, merchantOffers, 0, this.getVillagerXp(), this.showProgressBar(), this.canRestock());
        }
    }

    private boolean needsToRestock() {
        for (MerchantOffer merchantOffer : this.getOffers()) {
            if (!merchantOffer.needsRestock()) continue;
            return true;
        }
        return false;
    }

    private boolean allowedToRestock() {
        return this.numberOfRestocksToday == 0 || this.numberOfRestocksToday < 2 && this.level().getGameTime() > this.lastRestockGameTime + 2400L;
    }

    public boolean shouldRestock() {
        long l = this.lastRestockGameTime + 12000L;
        long m = this.level().getGameTime();
        boolean bl = m > l;
        long n = this.level().getDefaultClockTime();
        if (this.lastRestockCheckDayTime > 0L) {
            long p = n / 24000L;
            long o = this.lastRestockCheckDayTime / 24000L;
            bl |= p > o;
        }
        this.lastRestockCheckDayTime = n;
        if (bl) {
            this.lastRestockGameTime = m;
            this.resetNumberOfRestocks();
        }
        return this.allowedToRestock() && this.needsToRestock();
    }

    private void resetNumberOfRestocks() {
        this.catchUpDemand();
        this.numberOfRestocksToday = 0;
    }

    private void catchUpDemand() {
        int i = 2 - this.numberOfRestocksToday;
        if (i > 0) {
            for (MerchantOffer merchantOffer : this.getOffers()) {
                merchantOffer.resetUses();
            }
        }
        for (int j = 0; j < i; ++j) {
            this.updateDemand();
        }
        this.resendOffersToTradingPlayer();
    }

    private void updateDemand() {
        for (MerchantOffer merchantOffer : this.getOffers()) {
            merchantOffer.updateDemand();
        }
    }

    public boolean isTrading() {
        return this.tradingPlayer != null;
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    public void overrideXp(int i) {
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return null;
    }

    @Override
    public boolean isClientSide() {
        return this.level().isClientSide();
    }

    @Override
    public boolean stillValid(Player player) {
        return this.getTradingPlayer() == player && this.isAlive() && player.distanceToSqr(this) <= 16.0D;
    }

    @Override
    public ItemStack getPickResult() {
        var profession = this.getRibbitData().getProfession();

        if (profession.equals(RibbitProfessionModule.NITWIT)) {
            return new ItemStack(ItemModule.RIBBIT_NITWIT_SPAWN_EGG.get());
        } else if (profession.equals(RibbitProfessionModule.FISHERMAN)) {
            return new ItemStack(ItemModule.RIBBIT_FISHERMAN_SPAWN_EGG.get());
        } else if (profession.equals(RibbitProfessionModule.GARDENER)) {
            return new ItemStack(ItemModule.RIBBIT_GARDENER_SPAWN_EGG.get());
        } else if (profession.equals(RibbitProfessionModule.MERCHANT)) {
            return new ItemStack(ItemModule.RIBBIT_MERCHANT_SPAWN_EGG.get());
        } else if (profession.equals(RibbitProfessionModule.SORCERER)) {
            return new ItemStack(ItemModule.RIBBIT_SORCERER_SPAWN_EGG.get());
        }

        return SpawnEggItem.byId(this.getType())
                .map(spawnEggItem -> new ItemStack(spawnEggItem.value()))
                .orElse(ItemStack.EMPTY);
    }
}
