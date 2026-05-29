package com.yungnickyoung.minecraft.ribbits.entity;

import com.yungnickyoung.minecraft.ribbits.RibbitsCommon;
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
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
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
import java.util.Locale;
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
    private static final int BED_HOME_PATH_RETRY_MIN_TICKS = 40;
    private static final int BED_HOME_PATH_RETRY_JITTER_TICKS = 40;
    private static final int BED_HOME_PATH_RETRY_MAX_TICKS = 400;
    private static final int BED_HOME_MAX_PATH_FAILURES = 3;
    private static final int BED_HOME_UPPER_BUNK_MAX_PATH_FAILURES = 6;
    private static final int BED_HOME_VERTICAL_RANGE = 12;
    private static final int BED_HOME_BASIC_FLOOR_VERTICAL_DISTANCE = 3;
    private static final int BED_HOME_DEBUG_MAX_SLOT_DETAILS = 16;
    private static final int BED_HOME_DEBUG_PATH_TAIL_NODES = 8;
    private static final int BED_HOME_INITIAL_SEARCH_SPREAD_TICKS = 20;
    private static final int NIGHT_COMMUNITY_ANCHOR_SEARCH_RANGE = 96;
    private static final int NIGHT_SHELTER_WAIT_CARPET_SEARCH_RANGE = 48;
    private static final int NIGHT_SHELTER_WAIT_CARPET_CANDIDATE_LIMIT = 24;
    private static final double BED_HOME_PATH_PROGRESS_DISTANCE_SQR = 4.0D;
    private static final double BED_HOME_SNAP_HORIZONTAL_DISTANCE = 0.25D;
    private static final double BED_HOME_SNAP_VERTICAL_DISTANCE = 0.55D;
    private static final double BED_HOME_UPPER_BUNK_APPROACH_HORIZONTAL_DISTANCE = 0.85D;
    private static final double BED_HOME_UPPER_BUNK_APPROACH_VERTICAL_DISTANCE = 1.15D;
    private static final double BED_HOME_UPPER_BUNK_ADJACENT_HORIZONTAL_DISTANCE = 0.85D;
    private static final double BED_HOME_UPPER_BUNK_ADJACENT_VERTICAL_DISTANCE = 1.15D;
    private static final double BED_HOME_PARTIAL_PATH_MAX_HORIZONTAL_DISTANCE = 2.5D;
    private static final double NIGHT_SHELTER_WAIT_REACHED_DISTANCE = 0.9D;
    private static final double NIGHT_SHELTER_WAIT_CENTER_HORIZONTAL_DISTANCE = 0.12D;
    private static final double NIGHT_SHELTER_WAIT_CENTER_VERTICAL_DISTANCE = 0.35D;
    private static final double FLOATING_PLANT_NAVIGATION_NODE_REACHED_DISTANCE = 0.65D;
    private static final double SHELTER_DOOR_REACH_DISTANCE = 1.75D;
    private static final double SHELTER_DOOR_HOLD_DISTANCE = 2.25D;
    private static final int SHELTER_DOOR_OPEN_HOLD_TICKS = 60;
    private static final double DEFAULT_STEP_HEIGHT = 0.6D;
    // Temporary QA logging. Remove or disable before release commit.
    private static final boolean SHELTER_DEBUG_LOGS = true;

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
    private static final RawAnimation FISH = RawAnimation.begin().thenPlay("fishing");
    private static final RawAnimation FISH_HOLDING = RawAnimation.begin().thenPlay("fishing_holding");
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
    private final RibbitWaterCropsGoal waterCropsGoal = new RibbitWaterCropsGoal(this, 16.0d, 1.0f, 1200);
    private final RibbitFishGoal fishGoal = new RibbitFishGoal(this, 16.0d, 1.0f, 600, 1800);
    private final RibbitApplyBuffGoal applyBuffGoal = new RibbitApplyBuffGoal(this, 32.0d, 12000);

    private static final EntityDataAccessor<RibbitData> RIBBIT_DATA = SynchedEntityData.defineId(RibbitEntity.class, EntityDataSerializerModule.RIBBIT_DATA_SERIALIZER);
    private static final EntityDataAccessor<Boolean> PLAYING_INSTRUMENT = SynchedEntityData.defineId(RibbitEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> UMBRELLA_FALLING = SynchedEntityData.defineId(RibbitEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> WATERING = SynchedEntityData.defineId(RibbitEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FISHING = SynchedEntityData.defineId(RibbitEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> BUFFING = SynchedEntityData.defineId(RibbitEntity.class, EntityDataSerializers.BOOLEAN);

    // These fields are used to prevent threadlocking by accessing entityData on rendering thread
    private RibbitData sidedRibbitData = new RibbitData(RibbitProfessionModule.NITWIT, RibbitUmbrellaTypeModule.UMBRELLA_1, RibbitInstrumentModule.NONE);
    private boolean isPlayingInstrument = false;
    private boolean isUmbrellaFalling = false;
    private boolean isWatering = false;
    private boolean isFishing = false;
    private boolean isBuffing = false;

    // NOTE: Fields below here are used only on Server
    private int ticksPlayingMusic;

    private BlockPos homePosition;
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
    private final Set<BlockPos> shelterDoorsToClose = new HashSet<>();
    private final Map<BlockPos, Integer> shelterDoorHoldUntilTicks = new HashMap<>();
    private final Map<BlockPos, BedHomePathRetry> unreachableBedHomeRetries = new HashMap<>();

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
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new RibbitTradeWithPlayerGoal(this));
        this.goalSelector.addGoal(0, new RibbitLookAtTradingPlayerGoal(this));
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new RibbitGoHomeGoal(this, 1.8f, 1f));
        this.goalSelector.addGoal(1, new RibbitWaitInShelterGoal(this, 1.0D));
        this.goalSelector.addGoal(2, new PanicGoal(this, 1.5D));
        this.goalSelector.addGoal(3, new RibbitStopAndStareAtFrogGoal(this, 4.0F));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RibbitStrollGoal(this, 1.0D, 16));
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

            this.tryRestAtHomeBed();
            this.tickDoorNavigationAssist();
            this.tickShelterDoors();
            this.tickFloatingPlantNavigationAssist();
            this.tickBasicBedHomeState();
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
        builder.define(BUFFING, false);
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
        } else if (WATERING.equals(dataAccessor)) {
            this.isWatering = this.entityData.get(WATERING);
        } else if (BUFFING.equals(dataAccessor)) {
            this.isBuffing = this.entityData.get(BUFFING);
        }
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        EntitySpawnReason entitySpawnReason, @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, entitySpawnReason, groupData);
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
        this.homePositionSetByPlayer = setByPlayer;
        this.homePositionIsAutomaticBed = automaticBed;
        this.initialBedHomeSearchScheduled = false;
        this.clearAutomaticBedHomePathFailures();

        if (setByPlayer) {
            this.debugShelter("manual home set for {} home={}", this.getShelterDebugLabel(), this.homePosition);
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
        this.debugShelter("bed candidate selected {} candidate={} approach={} part={} stack={} distanceSqr={}",
                this.getShelterDebugLabel(),
                this.pendingBedHomePosition,
                this.pendingBedHomeApproachPosition,
                this.getBedPartName(this.pendingBedHomePosition),
                this.getBedStackName(this.pendingBedHomePosition),
                this.pendingBedHomePosition.distSqr(this.blockPosition()));
    }

    private void resetHomeRelatedGoals(boolean blockBandRejoin) {
        this.fishGoal.resetTarget();
        this.waterCropsGoal.resetTarget();
        this.musicGoal.resetTarget(blockBandRejoin);
        this.getNavigation().stop();
    }

    public boolean tryAssignShelterHome() {
        if (!this.isShelterNight() || this.homePositionSetByPlayer) {
            return false;
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
            this.debugShelter("bed slot released {} slot={} reason=invalid_or_claimed",
                    this.getShelterDebugLabel(),
                    this.pendingBedHomePosition);
            this.pendingBedHomePosition = null;
            this.pendingBedHomeApproachPosition = null;
            this.activeBedHomeApproachPosition = null;
        }

        this.scheduleNextBedHomeSearch();
        this.debugShelter("basic bed search start {} currentHome={} currentHomeValid={}",
                this.getShelterDebugLabel(), this.getHomePosition(), this.hasValidAutomaticBedHome());
        BedHomeSearchResult bedHome = this.findNearbyBedHomeSlot();
        if (bedHome.selectedHome().isPresent()) {
            this.clearNightCommunityAnchor();
            this.clearNightShelterWait();
            BedHomePathSelection newHome = bedHome.selectedHome().get();
            this.setPendingBedHome(newHome.slot(), newHome.approach());
        } else {
            if (bedHome.pendingClaims() > 0) {
                this.schedulePendingBedHomeSearch();
            } else {
                this.scheduleUnavailableBedHomeSearch();
            }

            this.updateNightShelterWaitTarget();
            if (this.hasActiveNightShelterWait()) {
                this.clearNightCommunityAnchor();
            } else {
                this.updateNightCommunityAnchor();
            }

            this.debugShelter("basic bed search failed {} pendingClaims={} nextRetryTick={}",
                    this.getShelterDebugLabel(),
                    bedHome.pendingClaims(),
                    this.nextBedHomeSearchTick);
        }

        return bedHome.selectedHome().isPresent();
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
        this.debugShelter("bed initial search staggered {} retryTick={}",
                this.getShelterDebugLabel(),
                this.nextBedHomeSearchTick);
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
            this.debugShelter("night community anchor selected {} anchor={} from={}",
                    this.getShelterDebugLabel(),
                    this.nightCommunityAnchorPosition,
                    nearbyShelteredRibbit.get().getShelterDebugOwnerLabel());
        }
    }

    private void clearNightCommunityAnchor() {
        this.nightCommunityAnchorPosition = null;
    }

    private void updateNightShelterWaitTarget() {
        if (!this.isShelterNight() || this.hasUsableHomePosition()) {
            this.clearNightShelterWait();
            return;
        }

        if (this.nightShelterWaitPosition != null
                && this.isAvailableNightShelterWaitCarpet(this.nightShelterWaitPosition)) {
            this.debugShelter("night shelter wait retained {} waitPos={} retryTick={}",
                    this.getShelterDebugLabel(),
                    this.nightShelterWaitPosition,
                    this.nextBedHomeSearchTick);
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
            this.debugShelter("night shelter wait selected {} waitPos={} retryTick={}",
                    this.getShelterDebugLabel(),
                    this.nightShelterWaitPosition,
                    this.nextBedHomeSearchTick);
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
        int carpets = 0;
        int blockedAbove = 0;
        int skyVisible = 0;
        int collisionBlocked = 0;
        int occupied = 0;

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!this.level().getBlockState(pos).is(BlockTags.WOOL_CARPETS)) {
                continue;
            }

            carpets++;
            if (!this.hasNightShelterWaitHeadroom(pos)) {
                blockedAbove++;
                continue;
            }

            if (!this.isNightShelterWaitInterior(pos)) {
                skyVisible++;
                continue;
            }

            if (!this.hasNightShelterWaitBodySpace(pos)) {
                collisionBlocked++;
                continue;
            }

            if (this.isNightShelterWaitOccupied(pos)) {
                occupied++;
                continue;
            }

            availableCarpets.add(pos.immutable());
        }

        List<BlockPos> candidates = availableCarpets.stream()
                .sorted(Comparator.comparingDouble(pos -> pos.distSqr(origin)))
                .limit(NIGHT_SHELTER_WAIT_CARPET_CANDIDATE_LIMIT)
                .toList();

        if (candidates.isEmpty()) {
            this.debugShelter("night shelter carpet search empty {} scannedCarpets={} blockedAbove={} skyVisible={} collisionBlocked={} occupied={}",
                    this.getShelterDebugLabel(),
                    carpets,
                    blockedAbove,
                    skyVisible,
                    collisionBlocked,
                    occupied);
            return Optional.empty();
        }

        Path path = this.getNavigation().createPath(new LinkedHashSet<>(candidates), 0);
        boolean progressivePath = path != null
                && !path.canReach()
                && this.isProgressivePartialNightShelterWaitPath(path);
        if (path == null || (!path.canReach() && !progressivePath)) {
            this.debugShelter("night shelter carpet path failed {} candidates={} scannedCarpets={} blockedAbove={} skyVisible={} collisionBlocked={} occupied={} target={} end={} canReach={} progressive={}",
                    this.getShelterDebugLabel(),
                    candidates.size(),
                    carpets,
                    blockedAbove,
                    skyVisible,
                    collisionBlocked,
                    occupied,
                    path == null ? null : path.getTarget(),
                    path == null || path.getEndNode() == null ? null : path.getEndNode().asBlockPos(),
                    path != null && path.canReach(),
                    progressivePath);
            return Optional.empty();
        }

        this.debugShelter("night shelter carpet path selected {} candidates={} scannedCarpets={} target={} end={} canReach={} progressive={}",
                this.getShelterDebugLabel(),
                candidates.size(),
                carpets,
                path.getTarget(),
                path.getEndNode() == null ? null : path.getEndNode().asBlockPos(),
                path.canReach(),
                progressivePath);
        return Optional.of(path.getTarget().immutable());
    }

    private boolean isAvailableNightShelterWaitCarpet(BlockPos pos) {
        return this.level().getBlockState(pos).is(BlockTags.WOOL_CARPETS)
                && this.hasNightShelterWaitHeadroom(pos)
                && this.isNightShelterWaitInterior(pos)
                && this.hasNightShelterWaitBodySpace(pos)
                && !this.isNightShelterWaitOccupied(pos);
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
        return !this.level().getEntitiesOfClass(
                        RibbitEntity.class,
                        new AABB(pos).inflate(0.35D, 0.5D, 0.35D),
                        ribbit -> ribbit != this && ribbit.isAlive())
                .isEmpty();
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
    }

    public boolean isAtShelterTarget(BlockPos shelterPosition) {
        if (this.homePositionSetByPlayer) {
            return shelterPosition.closerToCenterThan(this.position(), 1.8D);
        }

        if (this.isValidBedHomeSlot(shelterPosition)) {
            return this.isCloseEnoughToBedHomeRestPosition(this.getBedHomeRestPosition(shelterPosition))
                    || this.isNearUpperBunkBedHomeApproach(shelterPosition);
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
                && !this.isVehicle()
                && !this.isLeashed()
                && !this.hasUsableHomePosition()
                && this.hasActiveNightShelterWait()
                && !this.isNightShelterWaitReadyToRetry();
    }

    public boolean isNightShelterWaitReadyToRetry() {
        if (!this.nightShelterWaitReached || this.tickCount < this.nextBedHomeSearchTick) {
            return false;
        }

        boolean canRetry = this.canUpdateGroundNavigationNow();
        if (!canRetry && SHELTER_DEBUG_LOGS && this.tickCount % 20 == 0) {
            this.debugShelter("night shelter wait retry delayed {} waitPos={} onGround={} inLiquid={} passenger={} delta={}",
                    this.getShelterDebugLabel(),
                    this.nightShelterWaitPosition,
                    this.onGround(),
                    this.isInLiquid(),
                    this.isPassenger(),
                    this.getDeltaMovement());
        }

        return canRetry;
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
        this.debugShelter("night shelter wait reached {} waitPos={} restPos={} retryTick={} recentered={}",
                this.getShelterDebugLabel(),
                this.nightShelterWaitPosition,
                restPosition,
                this.nextBedHomeSearchTick,
                recentered);
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
        if (SHELTER_DEBUG_LOGS && this.tickCount % 20 == 0) {
            this.debugShelter("night shelter wait fine approach {} waitPos={} restDist={} speed={}",
                    this.getShelterDebugLabel(),
                    this.nightShelterWaitPosition,
                    this.formatDistanceTo(restPosition),
                    String.format(Locale.ROOT, "%.2f", speedModifier));
        }
        return true;
    }

    private boolean isNearNightShelterWaitRestPosition(Vec3 restPosition) {
        double dx = this.getX() - restPosition.x;
        double dz = this.getZ() - restPosition.z;
        double dy = Math.abs(this.getY() - restPosition.y);
        return dx * dx + dz * dz <= Mth.square(NIGHT_SHELTER_WAIT_CENTER_HORIZONTAL_DISTANCE)
                && dy <= NIGHT_SHELTER_WAIT_CENTER_VERTICAL_DISTANCE;
    }

    private boolean canUpdateGroundNavigationNow() {
        return this.onGround() || this.isInLiquid() || this.isPassenger();
    }

    public void handleNightShelterWaitPathFailed(String reason) {
        if (this.nightShelterWaitPosition != null) {
            this.debugShelter("night shelter wait failed {} waitPos={} reason={}",
                    this.getShelterDebugLabel(),
                    this.nightShelterWaitPosition,
                    reason);
        }
        this.clearNightShelterWait();
        this.scheduleNextBedHomeSearch();
    }

    @Nullable
    public Path createNightShelterWaitNavigationPath(BlockPos waitPosition) {
        this.debugPathPreflightIfNavigationCannotUpdate("night_shelter_wait:" + waitPosition.toShortString(), Set.of(waitPosition));
        Path path = this.getNavigation().createPath(waitPosition, 0);
        boolean progressivePath = path != null
                && !path.canReach()
                && this.isProgressivePartialNightShelterWaitPath(path);
        if (path == null || (!path.canReach() && !progressivePath)) {
            this.debugShelter("night shelter wait navigation path rejected {} waitPos={} target={} end={} canReach={} progressive={} distToTarget={} nodeCount={}",
                    this.getShelterDebugLabel(),
                    waitPosition,
                    path == null ? null : path.getTarget(),
                    path == null || path.getEndNode() == null ? null : path.getEndNode().asBlockPos(),
                    path != null && path.canReach(),
                    progressivePath,
                    path == null ? null : path.getDistToTarget(),
                    path == null ? 0 : path.getNodeCount());
            return null;
        }

        this.debugShelter("night shelter wait navigation path accepted {} waitPos={} target={} end={} canReach={} progressive={} distToTarget={} nodeCount={}",
                this.getShelterDebugLabel(),
                waitPosition,
                path.getTarget(),
                path.getEndNode() == null ? null : path.getEndNode().asBlockPos(),
                path.canReach(),
                progressivePath,
                path.getDistToTarget(),
                path.getNodeCount());
        return path;
    }

    public boolean hasBedHomeNavigationTarget() {
        return !this.homePositionSetByPlayer
                && (this.hasValidAutomaticBedHome() || this.hasValidPendingBedHome());
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

        return this.blockPosition();
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
        if (this.isShelterNight() || this.isVehicle() || this.isLeashed()) {
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
                this.resolveBedHomeApproachFromPath(approachPositions, path)
                        .ifPresent(approachPosition -> this.activeBedHomeApproachPosition = approachPosition);
                if (path != null && !path.canReach()
                        && !this.isAcceptablePartialBedHomePath(path)
                        && !this.isProgressivePartialBedHomePath(path)) {
                    this.debugShelter("bed navigation partial rejected {} home={} target={} end={} distToTarget={}",
                            this.getShelterDebugLabel(),
                            homePosition,
                            path.getTarget(),
                            path.getEndNode() == null ? null : path.getEndNode().asBlockPos(),
                            path.getDistToTarget());
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
                && this.hasBedHomeApproachSpace(this.pendingBedHomeApproachPosition)) {
            return this.pendingBedHomeApproachPosition;
        }

        if (this.activeBedHomeApproachPosition != null
                && this.getBedHomeApproachPositions(homePosition).contains(this.activeBedHomeApproachPosition)
                && this.hasBedHomeApproachSpace(this.activeBedHomeApproachPosition)) {
            return this.activeBedHomeApproachPosition;
        }

        return null;
    }

    private Optional<BlockPos> resolveBedHomeApproachFromPath(
            Set<BlockPos> approachPositions,
            @Nullable Path path) {
        if (path == null) {
            return Optional.empty();
        }

        BlockPos target = path.getTarget();
        if (approachPositions.contains(target) && this.hasBedHomeApproachSpace(target)) {
            return Optional.of(target.immutable());
        }

        BlockPos reference = path.getEndNode() == null ? target : path.getEndNode().asBlockPos();
        return approachPositions.stream()
                .filter(this::hasBedHomeApproachSpace)
                .filter(approachPosition -> this.isBedHomeApproachCloseToPathEnd(approachPosition, reference))
                .min(Comparator.comparingDouble(approachPosition -> approachPosition.distSqr(reference)))
                .map(BlockPos::immutable);
    }

    public void handleShelterPathFailed(BlockPos homePosition, String reason) {
        if (this.level().isClientSide() || this.homePositionSetByPlayer) {
            return;
        }

        this.debugShelter("basic bed path failed {} home={} reason={} pending={} automatic={} distanceSqr={}",
                this.getShelterDebugLabel(),
                homePosition,
                reason,
                homePosition.equals(this.pendingBedHomePosition),
                homePosition.equals(this.homePosition) && this.homePositionIsAutomaticBed,
                homePosition.distSqr(this.blockPosition()));
        this.markBedHomePathRetry(homePosition, reason);

        if (homePosition.equals(this.pendingBedHomePosition)) {
            this.pendingBedHomePosition = null;
            this.pendingBedHomeApproachPosition = null;
            this.activeBedHomeApproachPosition = null;
            this.scheduleNextBedHomeSearch();
        } else if (this.homePositionIsAutomaticBed && homePosition.equals(this.homePosition)) {
            this.trackAutomaticBedHomePathFailure(homePosition, reason);
        }
    }

    public void handleShelterPathStarted(BlockPos homePosition) {
        if (this.homePositionIsAutomaticBed && homePosition.equals(this.homePosition)) {
            this.clearAutomaticBedHomePathFailures();
        }
    }

    private void trackAutomaticBedHomePathFailure(BlockPos homePosition, String reason) {
        if (!homePosition.equals(this.failingAutomaticBedHomePosition)) {
            this.failingAutomaticBedHomePosition = homePosition.immutable();
            this.automaticBedHomePathFailures = 0;
        }

        this.automaticBedHomePathFailures++;
        int maxFailures = this.getAutomaticBedHomeMaxPathFailures(homePosition);
        this.debugShelter("automatic bed return retry {} home={} reason={} failures={}/{} stack={}",
                this.getShelterDebugLabel(),
                homePosition,
                reason,
                this.automaticBedHomePathFailures,
                maxFailures,
                this.getBedStackName(homePosition));
        if (this.automaticBedHomePathFailures < maxFailures) {
            return;
        }

        this.debugShelter("automatic bed released {} home={} reason={} failures={} stack={}",
                this.getShelterDebugLabel(),
                homePosition,
                reason,
                this.automaticBedHomePathFailures,
                this.getBedStackName(homePosition));
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
                || this.isVehicle()
                || !this.hasBedHomeNavigationTarget()) {
            return false;
        }

        BlockPos home = this.getShelterNavigationPosition();
        if (home == null || !this.isUsableAutomaticBedHomeSlot(home)) {
            return false;
        }

        boolean reachedRestPosition = this.isCloseEnoughToBedHomeRestPosition(this.getBedHomeRestPosition(home));
        boolean reachedBedHomeApproach = !reachedRestPosition && this.isNearBedHomeApproach(home);
        boolean reachedUpperBunkApproach = !reachedRestPosition && !reachedBedHomeApproach && this.isNearUpperBunkBedHomeApproach(home);
        boolean reachedUpperBunkAdjacent = !reachedRestPosition
                && !reachedBedHomeApproach
                && !reachedUpperBunkApproach
                && this.isNearUpperBunkBedHomeAdjacentPosition(home);
        boolean pendingBedHome = home.equals(this.pendingBedHomePosition);
        if (!reachedRestPosition && !reachedBedHomeApproach && !reachedUpperBunkApproach && !reachedUpperBunkAdjacent) {
            return false;
        }

        if (reachedBedHomeApproach || reachedUpperBunkApproach || reachedUpperBunkAdjacent) {
            this.debugShelter("bed approach accepted {} home={} approach={} part={} stack={}",
                    this.getShelterDebugLabel(),
                    home,
                    this.getPreferredBedHomeApproachPosition(home),
                    this.getBedPartName(home),
                    this.getBedStackName(home));
        }

        this.holdAtBedHome(home);
        if (pendingBedHome) {
            this.debugShelter("bed home committed {} home={} part={} stack={}",
                    this.getShelterDebugLabel(),
                    home,
                    this.getBedPartName(home),
                    this.getBedStackName(home));
            this.setAutomaticBedHome(home);
        }
        return true;
    }

    private BedHomeSearchResult findNearbyBedHomeSlot() {
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
        this.debugShelter("basic bed search sources {} poiSlots={}",
                this.getShelterDebugLabel(),
                sortedBedSlots.size());
        return this.findNearbyBedHomeSlot(sortedBedSlots);
    }

    private BedHomeSearchResult findNearbyBedHomeSlot(List<BlockPos> bedSlots) {
        int occupiedSlots = 0;
        int pendingClaims = 0;
        int blockedSlots = 0;
        int upperFloorSlots = 0;
        int pathCooldownSlots = 0;
        int pathBatchSkippedSlots = 0;
        List<BlockPos> pathCandidates = new ArrayList<>(BED_HOME_PATHFIND_BATCH_SIZE);
        List<BedHomeSlotDebug> unavailableSlots = new ArrayList<>();

        this.clearExpiredBedHomePathRetries();
        for (BlockPos bedSlot : bedSlots) {
            if (!this.hasBedHomeBodySpace(bedSlot)) {
                blockedSlots++;
                unavailableSlots.add(this.getBedHomeSlotDebug(bedSlot, this.getBedHomeBodySpaceBlockReason(bedSlot)));
                continue;
            }

            if (!this.isBasicFloorBedHomeSlot(bedSlot)) {
                upperFloorSlots++;
                unavailableSlots.add(this.getBedHomeSlotDebug(bedSlot, "upper_floor_ignored"));
                continue;
            }

            Optional<BedHomeSlotClaim> claim = this.getBedHomeSlotClaim(bedSlot);
            if (claim.isPresent()) {
                occupiedSlots++;
                if (claim.get().pendingHome()) {
                    pendingClaims++;
                }
                unavailableSlots.add(this.getBedHomeSlotDebug(bedSlot, this.getBedHomeSlotClaimReason(claim.get())));
                continue;
            }

            if (this.isBedHomePathRetryCoolingDown(bedSlot)) {
                pathCooldownSlots++;
                unavailableSlots.add(this.getBedHomeSlotDebug(bedSlot, this.getBedHomePathRetryReason(bedSlot)));
                continue;
            }

            if (pathCandidates.size() >= BED_HOME_PATHFIND_BATCH_SIZE) {
                pathBatchSkippedSlots++;
                unavailableSlots.add(this.getBedHomeSlotDebug(bedSlot, "path_batch_limit"));
                continue;
            }

            pathCandidates.add(bedSlot);
        }

        Optional<BedHomePathSelection> reachableSlot = this.findReachableBedHomeSlot(pathCandidates);
        if (reachableSlot.isPresent()) {
            BedHomePathSelection bedHome = reachableSlot.get();
            BlockPos bedSlot = bedHome.slot();
            this.debugShelter("basic bed slot selected for {} slot={} approach={} part={} stack={} occupiedSkipped={} blockedSkipped={} upperFloorSkipped={} pathCooldownSkipped={} pathBatchSkipped={} pathCandidates={}",
                    this.getShelterDebugLabel(),
                    bedSlot,
                    bedHome.approach(),
                    this.getBedPartName(bedSlot),
                    this.getBedStackName(bedSlot),
                    occupiedSlots,
                    blockedSlots,
                    upperFloorSlots,
                    pathCooldownSlots,
                    pathBatchSkippedSlots,
                    pathCandidates.size());
            return new BedHomeSearchResult(Optional.of(bedHome), pendingClaims, bedSlots.size());
        }

        if (!pathCandidates.isEmpty()) {
            pathCandidates.forEach(candidate -> {
                this.markBedHomePathRetry(candidate, "path_unreachable_batch");
                unavailableSlots.add(this.getBedHomeSlotDebug(candidate, "path_unreachable"));
            });
        }

        this.debugShelter("basic bed slot pass failed for {} part={} total={} occupiedSkipped={} blockedSkipped={} upperFloorSkipped={} pathCooldownSkipped={} pathBatchSkipped={} pathCandidates={}",
                this.getShelterDebugLabel(),
                "any",
                bedSlots.size(),
                occupiedSlots,
                blockedSlots,
                upperFloorSlots,
                pathCooldownSlots,
                pathBatchSkippedSlots,
                pathCandidates.size());
        this.debugNightBedSlotFailures(unavailableSlots);
        return new BedHomeSearchResult(Optional.empty(), pendingClaims, bedSlots.size());
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
            this.debugShelter("bed path detail {} slots={} approaches=0 reason=no_valid_approaches",
                    this.getShelterDebugLabel(),
                    bedSlots.size());
            return Optional.empty();
        }

        this.debugBedHomePathPreflight("batch", slotByApproach.keySet());
        Path path = this.getNavigation().createPath(slotByApproach.keySet(), 0);
        if (path == null) {
            this.debugBedHomePathAttempt(bedSlots, slotByApproach, null);
            this.debugIndividualBedHomeApproachPaths(slotByApproach);
            return Optional.empty();
        }

        if (!path.canReach()) {
            this.debugBedHomePathAttempt(bedSlots, slotByApproach, path);
            boolean progressivePartialPath = this.isProgressivePartialBedHomePath(path);
            if (!this.isAcceptablePartialBedHomePath(path) && !progressivePartialPath) {
                this.debugRejectedBedHomePath(bedSlots, slotByApproach, path);
                return Optional.empty();
            }

            this.debugShelter("bed partial path accepted {} target={} end={} distToTarget={} progressive={} candidates={}",
                    this.getShelterDebugLabel(),
                    path.getTarget(),
                    path.getEndNode() == null ? null : path.getEndNode().asBlockPos(),
                    path.getDistToTarget(),
                    progressivePartialPath,
                    bedSlots.size());
        }

        return this.resolveBedHomePathSelection(path, slotByApproach);
    }

    private Optional<BedHomePathSelection> resolveBedHomePathSelection(
            Path path,
            Map<BlockPos, BlockPos> slotByApproach) {
        BlockPos target = path.getTarget();
        BlockPos matchingSlot = slotByApproach.get(target);
        if (matchingSlot != null && this.hasBedHomeApproachSpace(target)) {
            return Optional.of(new BedHomePathSelection(matchingSlot, target.immutable()));
        }

        BlockPos reference = path.getEndNode() == null ? target : path.getEndNode().asBlockPos();
        return slotByApproach.entrySet().stream()
                .filter(entry -> this.hasBedHomeApproachSpace(entry.getKey()))
                .filter(entry -> this.isBedHomeApproachCloseToPathEnd(entry.getKey(), reference))
                .min(Comparator.comparingDouble(entry -> entry.getKey().distSqr(reference)))
                .map(entry -> new BedHomePathSelection(entry.getValue(), entry.getKey()));
    }

    private void debugBedHomePathAttempt(
            List<BlockPos> bedSlots,
            Map<BlockPos, BlockPos> slotByApproach,
            @Nullable Path path) {
        this.debugShelter("bed path detail {} slots={} approaches={} pathNull={} target={} end={} canReach={} distToTarget={} nodeCount={}",
                this.getShelterDebugLabel(),
                bedSlots.size(),
                slotByApproach.size(),
                path == null,
                path == null ? null : path.getTarget(),
                path == null || path.getEndNode() == null ? null : path.getEndNode().asBlockPos(),
                path != null && path.canReach(),
                path == null ? null : path.getDistToTarget(),
                path == null ? 0 : path.getNodeCount());

        bedSlots.stream()
                .limit(BED_HOME_DEBUG_MAX_SLOT_DETAILS)
                .forEach(bedSlot -> this.debugBedHomeApproachPositions(bedSlot, slotByApproach));
    }

    private void debugBedHomePathPreflight(String context, Set<BlockPos> targets) {
        if (!SHELTER_DEBUG_LOGS) {
            return;
        }

        BlockPos blockPos = this.blockPosition();
        BlockPos onPos = this.getOnPos();
        BlockState feetState = this.level().getBlockState(blockPos);
        BlockState belowState = this.level().getBlockState(blockPos.below());
        BlockState onState = this.level().getBlockState(onPos);
        boolean canUpdateLikeGroundNavigation = this.onGround() || this.isInLiquid() || this.isPassenger();
        this.debugShelter("bed path preflight {} context={} targets={} canUpdateLikeGroundNav={} onGround={} inLiquid={} inWater={} passenger={} delta={} block={} feet={} below={} onPos={} onState={} supportY={} waitReached={} waitPos={}",
                this.getShelterDebugLabel(),
                context,
                targets.size(),
                canUpdateLikeGroundNavigation,
                this.onGround(),
                this.isInLiquid(),
                this.isInWater(),
                this.isPassenger(),
                this.getDeltaMovement(),
                blockPos,
                feetState.getBlock(),
                belowState.getBlock(),
                onPos,
                onState.getBlock(),
                this.getSupportSurfaceY(blockPos),
                this.nightShelterWaitReached,
                this.nightShelterWaitPosition);
        this.debugPathPreflightIfNavigationCannotUpdate(context, targets);
    }

    private void debugPathPreflightIfNavigationCannotUpdate(String context, Set<BlockPos> targets) {
        if (!SHELTER_DEBUG_LOGS || this.canUpdateGroundNavigationNow()) {
            return;
        }

        BlockPos blockPos = this.blockPosition();
        BlockPos onPos = this.getOnPos();
        AABB boundingBox = this.getBoundingBox();
        this.debugShelter("navigation airborne context {} context={} targets={} precisePos={} bboxMinY={} bboxMaxY={} delta={} blockContext={} onContext={}",
                this.getShelterDebugLabel(),
                context,
                targets.size(),
                this.position(),
                String.format(Locale.ROOT, "%.3f", boundingBox.minY),
                String.format(Locale.ROOT, "%.3f", boundingBox.maxY),
                this.getDeltaMovement(),
                this.getPathNeighborhoodDebug(blockPos),
                this.getPathNeighborhoodDebug(onPos));
    }

    private void debugIndividualBedHomeApproachPaths(Map<BlockPos, BlockPos> slotByApproach) {
        if (!SHELTER_DEBUG_LOGS) {
            return;
        }

        slotByApproach.entrySet().stream()
                .limit(BED_HOME_DEBUG_MAX_SLOT_DETAILS)
                .forEach(entry -> {
                    BlockPos approach = entry.getKey();
                    this.debugBedHomePathPreflight("single:" + approach.toShortString(), Set.of(approach));
                    Path singlePath = this.getNavigation().createPath(approach, 0);
                    this.debugShelter("bed single approach path {} slot={} approach={} pathNull={} target={} end={} canReach={} distToTarget={} nodeCount={} approachDetail={}",
                            this.getShelterDebugLabel(),
                            entry.getValue(),
                            approach,
                            singlePath == null,
                            singlePath == null ? null : singlePath.getTarget(),
                            singlePath == null || singlePath.getEndNode() == null ? null : singlePath.getEndNode().asBlockPos(),
                            singlePath != null && singlePath.canReach(),
                            singlePath == null ? null : singlePath.getDistToTarget(),
                            singlePath == null ? 0 : singlePath.getNodeCount(),
                            this.getBedHomeApproachDebugString(approach, entry.getValue(), slotByApproach));

                    if (singlePath != null && !singlePath.canReach()) {
                        this.debugBedHomePathTail("single:" + approach.toShortString(), singlePath);
                    }
                });
    }

    private void debugRejectedBedHomePath(
            List<BlockPos> bedSlots,
            Map<BlockPos, BlockPos> slotByApproach,
            Path path) {
        if (!SHELTER_DEBUG_LOGS) {
            return;
        }

        this.debugClosestBedHomeApproachesToPathEnd(slotByApproach, path);
        this.debugBedHomePathTail("batch_rejected", path);
        this.debugIndividualBedHomeApproachPaths(slotByApproach);

        BlockPos endPos = path.getEndNode() == null ? null : path.getEndNode().asBlockPos();
        if (endPos != null) {
            this.debugShelter("bed rejected path end context {} end={} context={}",
                    this.getShelterDebugLabel(),
                    endPos,
                    this.getPathNeighborhoodDebug(endPos));
        }

        bedSlots.stream()
                .limit(BED_HOME_DEBUG_MAX_SLOT_DETAILS)
                .forEach(bedSlot -> this.debugShelter("bed rejected slot context {} slot={} part={} stack={} block={} below={} above={} claim={} pathRetry={}",
                        this.getShelterDebugLabel(),
                        bedSlot,
                        this.getBedPartName(bedSlot),
                        this.getBedStackName(bedSlot),
                        this.level().getBlockState(bedSlot).getBlock(),
                        this.level().getBlockState(bedSlot.below()).getBlock(),
                        this.level().getBlockState(bedSlot.above()).getBlock(),
                        this.getBedHomeSlotClaim(bedSlot)
                                .map(this::getBedHomeSlotClaimReason)
                                .orElse("free"),
                        this.getBedHomePathRetryReasonForDebug(bedSlot)));
    }

    private void debugClosestBedHomeApproachesToPathEnd(Map<BlockPos, BlockPos> slotByApproach, Path path) {
        if (path.getEndNode() == null) {
            return;
        }

        BlockPos endPos = path.getEndNode().asBlockPos();
        List<String> closestApproaches = slotByApproach.entrySet().stream()
                .sorted(Comparator.comparingDouble(entry -> entry.getKey().distSqr(endPos)))
                .limit(BED_HOME_DEBUG_MAX_SLOT_DETAILS)
                .map(entry -> entry.getKey().toShortString()
                        + "/slot=" + entry.getValue().toShortString()
                        + "/dist=" + String.format("%.2f", Math.sqrt(entry.getKey().distSqr(endPos)))
                        + "/close=" + this.isBedHomeApproachCloseToPathEnd(entry.getKey(), endPos)
                        + "/space=" + this.hasBedHomeApproachSpace(entry.getKey())
                        + "/context=" + this.getPathProbeDebug(entry.getKey()))
                .toList();

        this.debugShelter("bed rejected closest approaches {} end={} approaches={}",
                this.getShelterDebugLabel(),
                endPos,
                closestApproaches);
    }

    private void debugBedHomePathTail(String context, Path path) {
        if (!SHELTER_DEBUG_LOGS) {
            return;
        }

        int nodeCount = path.getNodeCount();
        int start = Math.max(0, nodeCount - BED_HOME_DEBUG_PATH_TAIL_NODES);
        List<String> nodes = new ArrayList<>();
        for (int i = start; i < nodeCount; i++) {
            Node node = path.getNode(i);
            BlockPos nodePos = node.asBlockPos();
            nodes.add(i + ":" + nodePos.toShortString() + "/" + this.getPathProbeDebug(nodePos));
        }

        this.debugShelter("bed path tail {} context={} target={} end={} canReach={} distToTarget={} nodes={}",
                this.getShelterDebugLabel(),
                context,
                path.getTarget(),
                path.getEndNode() == null ? null : path.getEndNode().asBlockPos(),
                path.canReach(),
                path.getDistToTarget(),
                nodes);
    }

    private String getPathNeighborhoodDebug(BlockPos center) {
        List<String> probes = new ArrayList<>();
        probes.add("center=" + this.getPathProbeDebug(center));
        probes.add("below=" + this.getPathProbeDebug(center.below()));
        probes.add("above=" + this.getPathProbeDebug(center.above()));
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos relative = center.relative(direction);
            probes.add(direction.getName() + "=" + this.getPathProbeDebug(relative));
        }
        return probes.toString();
    }

    private String getPathProbeDebug(BlockPos pos) {
        return pos.toShortString()
                + "/feet=" + this.getPathBlockDebug(pos)
                + "/below=" + this.getPathBlockDebug(pos.below())
                + "/above=" + this.getPathBlockDebug(pos.above())
                + "/space=" + this.hasBedHomeApproachSpace(pos)
                + "/supportY=" + String.format(Locale.ROOT, "%.3f", this.getSupportSurfaceY(pos));
    }

    private String getPathBlockDebug(BlockPos pos) {
        BlockState state = this.level().getBlockState(pos);
        var collisionShape = state.getCollisionShape(this.level(), pos);
        double collisionTop = collisionShape.isEmpty() ? 0.0D : collisionShape.max(Direction.Axis.Y);
        StringBuilder builder = new StringBuilder(state.getBlock().toString());
        builder.append("{air=").append(state.isAir())
                .append(",motion=").append(state.blocksMotion())
                .append(",collision=")
                .append(collisionShape.isEmpty() ? "empty" : String.format(Locale.ROOT, "%.3f", collisionTop));

        if (state.getBlock() instanceof DoorBlock && state.hasProperty(DoorBlock.OPEN)) {
            builder.append(",doorOpen=").append(state.getValue(DoorBlock.OPEN));
            if (state.hasProperty(DoorBlock.HALF)) {
                builder.append(",half=").append(state.getValue(DoorBlock.HALF));
            }
        }

        if (state.getBlock() instanceof FenceGateBlock && state.hasProperty(FenceGateBlock.OPEN)) {
            builder.append(",gateOpen=").append(state.getValue(FenceGateBlock.OPEN));
        }

        if (state.is(BlockTags.BEDS) && state.hasProperty(BedBlock.PART)) {
            builder.append(",bedPart=").append(state.getValue(BedBlock.PART));
            if (state.hasProperty(BedBlock.FACING)) {
                builder.append(",facing=").append(state.getValue(BedBlock.FACING));
            }
        }

        if (state.is(BlockTags.WOOL_CARPETS)) {
            builder.append(",carpet=true");
        }

        builder.append("}");
        return builder.toString();
    }

    private void debugBedHomeApproachPositions(BlockPos bedSlot, Map<BlockPos, BlockPos> slotByApproach) {
        List<String> approaches = this.getRawBedHomeApproachPositions(bedSlot).stream()
                .limit(BED_HOME_DEBUG_MAX_SLOT_DETAILS)
                .map(pos -> this.getBedHomeApproachDebugString(pos, bedSlot, slotByApproach))
                .toList();

        this.debugShelter("bed approach detail {} slot={} part={} stack={} approaches={}",
                this.getShelterDebugLabel(),
                bedSlot,
                this.getBedPartName(bedSlot),
                this.getBedStackName(bedSlot),
                approaches);
    }

    private String getBedHomeApproachDebugString(
            BlockPos pos,
            BlockPos bedSlot,
            Map<BlockPos, BlockPos> slotByApproach) {
        BlockState floorState = this.level().getBlockState(pos);
        BlockState supportState = this.level().getBlockState(pos.below());
        BlockState aboveState = this.level().getBlockState(pos.above());
        boolean selectedForSlot = bedSlot.equals(slotByApproach.get(pos));
        boolean hasCollisionSpace = this.level().noCollision(this, this.getBedHomeApproachBox(pos));
        return pos.toShortString()
                + "/selected=" + selectedForSlot
                + "/space=" + hasCollisionSpace
                + "/floor=" + floorState.getBlock()
                + "/support=" + supportState.getBlock()
                + "/above=" + aboveState.getBlock();
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
        boolean progresses = endDistanceSqr < startDistanceSqr - BED_HOME_PATH_PROGRESS_DISTANCE_SQR;
        if (SHELTER_DEBUG_LOGS) {
            this.debugShelter("bed partial path progress check {} target={} end={} startDistSqr={} endDistSqr={} progresses={}",
                    this.getShelterDebugLabel(),
                    target,
                    endNode.asBlockPos(),
                    String.format(Locale.ROOT, "%.2f", startDistanceSqr),
                    String.format(Locale.ROOT, "%.2f", endDistanceSqr),
                    progresses);
        }

        return progresses;
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
        boolean progresses = endDistanceSqr < startDistanceSqr - BED_HOME_PATH_PROGRESS_DISTANCE_SQR;
        if (SHELTER_DEBUG_LOGS) {
            this.debugShelter("night shelter partial path progress check {} target={} end={} startDistSqr={} endDistSqr={} progresses={}",
                    this.getShelterDebugLabel(),
                    target,
                    endNode.asBlockPos(),
                    String.format(Locale.ROOT, "%.2f", startDistanceSqr),
                    String.format(Locale.ROOT, "%.2f", endDistanceSqr),
                    progresses);
        }

        return progresses;
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
        return this.getRawBedHomeApproachPositions(bedSlot);
    }

    private Set<BlockPos> getValidBedHomeApproachPositions(BlockPos bedSlot) {
        Set<BlockPos> approachPositions = new LinkedHashSet<>();
        for (BlockPos approachPosition : this.getBedHomeApproachPositions(bedSlot)) {
            if (this.hasBedHomeApproachSpace(approachPosition)) {
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
        if (this.isUpperBunkBedHomeSlot(bedSlot)) {
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

    private boolean isBedHomeApproachCloseToPathEnd(BlockPos approachPosition, BlockPos pathEndPosition) {
        double horizontalDistanceSqr = Mth.square(approachPosition.getX() - pathEndPosition.getX())
                + Mth.square(approachPosition.getZ() - pathEndPosition.getZ());
        return horizontalDistanceSqr <= Mth.square(BED_HOME_PARTIAL_PATH_MAX_HORIZONTAL_DISTANCE)
                && Math.abs(approachPosition.getY() - pathEndPosition.getY()) <= 2;
    }

    private AABB getBedHomeApproachBox(BlockPos pos) {
        double halfWidth = this.getBbWidth() / 2.0D;
        return new AABB(
                pos.getX() + 0.5D - halfWidth,
                pos.getY() + 0.001D,
                pos.getZ() + 0.5D - halfWidth,
                pos.getX() + 0.5D + halfWidth,
                pos.getY() + this.getBbHeight(),
                pos.getZ() + 0.5D + halfWidth);
    }

    private boolean isBedHomePathRetryCoolingDown(BlockPos bedSlot) {
        BedHomePathRetry retry = this.unreachableBedHomeRetries.get(bedSlot);
        if (retry == null) {
            return false;
        }

        if (retry.isBlockedForNight()) {
            return true;
        }

        if (!retry.isStillValid(this.tickCount)) {
            this.unreachableBedHomeRetries.remove(bedSlot);
            return false;
        }

        if (!retry.shouldRetry(this.tickCount)) {
            return true;
        }

        return false;
    }

    private void markBedHomePathRetry(BlockPos bedSlot, String reason) {
        BlockPos immutableBedSlot = bedSlot.immutable();
        BedHomePathRetry retry = this.unreachableBedHomeRetries.get(immutableBedSlot);
        if (retry == null || (!retry.isBlockedForNight() && !retry.isStillValid(this.tickCount))) {
            retry = new BedHomePathRetry(this.random, this.tickCount);
            this.unreachableBedHomeRetries.put(immutableBedSlot, retry);
        }

        boolean blockedForNight = retry.markAttempt(this.tickCount, bedSlot.distSqr(this.blockPosition()));
        this.debugShelter("bed path retry cached {} slot={} reason={} retryTick={} noProgressFailures={} blockedForNight={}",
                this.getShelterDebugLabel(),
                bedSlot,
                reason,
                retry.nextScheduledAttemptTick(),
                retry.failuresWithoutProgress(),
                blockedForNight);
    }

    private void clearExpiredBedHomePathRetries() {
        this.unreachableBedHomeRetries.entrySet().removeIf(entry ->
                !entry.getValue().isBlockedForNight() && !entry.getValue().isStillValid(this.tickCount));
    }

    private String getBedHomePathRetryReason(BlockPos bedSlot) {
        BedHomePathRetry retry = this.unreachableBedHomeRetries.get(bedSlot);
        return retry != null && retry.isBlockedForNight() ? "path_blocked_for_night" : "path_retry_cooldown";
    }

    private String getBedHomePathRetryReasonForDebug(BlockPos bedSlot) {
        BedHomePathRetry retry = this.unreachableBedHomeRetries.get(bedSlot);
        if (retry == null) {
            return "none";
        }

        return (retry.isBlockedForNight() ? "blocked_for_night" : "cooldown")
                + "/next=" + retry.nextScheduledAttemptTick()
                + "/failures=" + retry.failuresWithoutProgress();
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

    private BedHomeSlotDebug getBedHomeSlotDebug(BlockPos bedSlot, String reason) {
        return new BedHomeSlotDebug(
                bedSlot.immutable(),
                this.getBedPartName(bedSlot),
                reason);
    }

    private String getBedHomeBodySpaceBlockReason(BlockPos bedSlot) {
        BlockState aboveState = this.level().getBlockState(bedSlot.above());
        if (!aboveState.isAir()) {
            if (this.isLowerBunkBedHomeSlot(bedSlot)) {
                return "lower_bunk_blocked_by_upper_bed";
            }

            return "blocked_above=" + aboveState;
        }

        return "body_collision";
    }

    private void debugNightBedSlotFailures(List<BedHomeSlotDebug> unavailableSlots) {
        if (!SHELTER_DEBUG_LOGS || !this.isShelterNight() || unavailableSlots.isEmpty()) {
            return;
        }

        List<String> details = unavailableSlots.stream()
                .limit(BED_HOME_DEBUG_MAX_SLOT_DETAILS)
                .map(BedHomeSlotDebug::toLogString)
                .toList();
        this.debugShelter("bed slot unavailable details for {} part={} shown={}/{} details={}",
                this.getShelterDebugLabel(),
                "any",
                details.size(),
                unavailableSlots.size(),
                details);
    }

    private boolean hasValidAutomaticBedHome() {
        BlockPos home = this.getHomePosition();
        return this.isAutomaticBedHomeRaw() && !this.hasHigherPriorityCommittedBedHomeClaim(home);
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
                && this.hasBedHomeApproachSpace(this.pendingBedHomeApproachPosition)
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
        if (SHELTER_DEBUG_LOGS && this.tickCount % 20 == 0) {
            this.debugShelter("floating plant navigation assist {} plant={} target={} nextNode={} targetBlock={}",
                    this.getShelterDebugLabel(),
                    floatingPlantPos,
                    target,
                    path.getNextNodePos(),
                    this.getNavigation().getTargetPos());
        }
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

    private String getBedPartName(BlockPos pos) {
        return this.isBedHomeSlot(pos) ? this.level().getBlockState(pos).getValue(BedBlock.PART).getSerializedName() : "none";
    }

    private String getBedStackName(BlockPos pos) {
        if (this.isLowerBunkBedHomeSlot(pos)) {
            return "lower_bunk";
        }

        if (this.isUpperBunkBedHomeSlot(pos)) {
            return "upper_bunk";
        }

        return "single";
    }

    private boolean hasBedHomeBodySpace(BlockPos pos) {
        if (this.isLowerBunkBedHomeSlot(pos)) {
            return false;
        }

        return this.level().noCollision(this, this.getBedHomeBodyBox(pos));
    }

    private boolean isBasicFloorBedHomeSlot(BlockPos pos) {
        return Math.abs(pos.getY() - this.blockPosition().getY()) <= BED_HOME_BASIC_FLOOR_VERTICAL_DISTANCE;
    }

    private AABB getBedHomeBodyBox(BlockPos pos) {
        Vec3 restPosition = this.getBedHomeRestPosition(pos);
        double halfWidth = this.getBbWidth() / 2.0D;
        return new AABB(
                restPosition.x - halfWidth,
                restPosition.y + 0.001D,
                restPosition.z - halfWidth,
                restPosition.x + halfWidth,
                restPosition.y + this.getBbHeight(),
                restPosition.z + halfWidth);
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

    private boolean isNearBedHomeApproach(BlockPos bedSlot) {
        BlockPos preferredApproachPosition = this.getPreferredBedHomeApproachPosition(bedSlot);
        if (preferredApproachPosition != null) {
            return this.isNearBlockCenter(
                    preferredApproachPosition,
                    BED_HOME_UPPER_BUNK_APPROACH_HORIZONTAL_DISTANCE,
                    BED_HOME_UPPER_BUNK_APPROACH_VERTICAL_DISTANCE);
        }

        return this.getBedHomeApproachPositions(bedSlot).stream()
                .filter(this::hasBedHomeApproachSpace)
                .anyMatch(pos -> this.isNearBlockCenter(
                        pos,
                        BED_HOME_UPPER_BUNK_APPROACH_HORIZONTAL_DISTANCE,
                        BED_HOME_UPPER_BUNK_APPROACH_VERTICAL_DISTANCE));
    }

    private boolean isNearUpperBunkBedHomeApproach(BlockPos bedSlot) {
        if (!this.isUpperBunkBedHomeSlot(bedSlot)) {
            return false;
        }

        return this.getBedHomeApproachPositions(bedSlot).stream()
                .anyMatch(pos -> this.isNearBlockCenter(
                        pos,
                        BED_HOME_UPPER_BUNK_APPROACH_HORIZONTAL_DISTANCE,
                        BED_HOME_UPPER_BUNK_APPROACH_VERTICAL_DISTANCE));
    }

    private boolean isNearUpperBunkBedHomeAdjacentPosition(BlockPos bedSlot) {
        if (!this.isUpperBunkBedHomeSlot(bedSlot)) {
            return false;
        }

        return this.getUpperBunkBedHomeAdjacentPositions(bedSlot).stream()
                .anyMatch(pos -> this.isNearBlockCenter(
                        pos,
                        BED_HOME_UPPER_BUNK_ADJACENT_HORIZONTAL_DISTANCE,
                        BED_HOME_UPPER_BUNK_ADJACENT_VERTICAL_DISTANCE));
    }

    private Set<BlockPos> getUpperBunkBedHomeAdjacentPositions(BlockPos bedSlot) {
        Set<BlockPos> positions = new LinkedHashSet<>();
        if (!this.isBedHomeSlot(bedSlot)) {
            return positions;
        }

        BlockState bedState = this.level().getBlockState(bedSlot);
        Direction bedFacing = bedState.getValue(BedBlock.FACING);
        BlockPos headPos = bedState.getValue(BedBlock.PART) == BedPart.HEAD
                ? bedSlot
                : bedSlot.relative(bedFacing);
        BlockPos footPos = headPos.relative(bedFacing.getOpposite());

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            positions.add(bedSlot.relative(direction).below().immutable());
            positions.add(headPos.relative(direction).below().immutable());
            positions.add(footPos.relative(direction).below().immutable());
        }

        return positions;
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

    private String formatDistanceTo(Vec3 position) {
        double dx = this.getX() - position.x;
        double dz = this.getZ() - position.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double verticalDistance = Math.abs(this.getY() - position.y);
        return String.format(Locale.ROOT, "h=%.2f/y=%.2f", horizontalDistance, verticalDistance);
    }

    private void holdAtBedHome(BlockPos home) {
        Vec3 restPosition = this.getBedHomeRestPosition(home);
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.setPos(restPosition.x, restPosition.y, restPosition.z);
    }

    public void openNearbyShelterDoors() {
        if (this.level().isClientSide() || !this.isShelterNight() || !this.hasUsableHomePosition()) {
            return;
        }

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        BlockPos origin = this.blockPosition();

        for (int y = -1; y <= 2; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    mutablePos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    this.setShelterDoorOpen(mutablePos, true);
                }
            }
        }
    }

    private void tickDoorNavigationAssist() {
        Path path = this.getNavigation().getPath();
        if (path != null && !path.isDone()) {
            int start = Math.max(0, path.getNextNodeIndex() - 1);
            int end = Math.min(path.getNextNodeIndex() + 2, path.getNodeCount() - 1);
            for (int i = start; i <= end; i++) {
                Node node = path.getNode(i);
                this.openShelterDoorAtPathNode(node.asBlockPos());
            }
        }

        if (this.horizontalCollision || (path != null && !path.isDone())) {
            this.openNearbyNavigationDoors();
        }
    }

    private void openShelterDoorAtPathNode(BlockPos pos) {
        this.setShelterDoorOpen(pos, true);
        this.setShelterDoorOpen(pos.above(), true);
        this.setShelterDoorOpen(pos.below(), true);
    }

    private void openNearbyNavigationDoors() {
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        BlockPos origin = this.blockPosition();

        for (int y = -1; y <= 2; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    mutablePos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    this.setShelterDoorOpen(mutablePos, true);
                }
            }
        }
    }

    private void tickShelterDoors() {
        if (this.tickCount % 20 != 0 || this.shelterDoorsToClose.isEmpty()) {
            return;
        }

        this.shelterDoorsToClose.removeIf(pos -> {
            if (this.shouldKeepShelterDoorOpen(pos)) {
                return false;
            }

            this.setShelterDoorOpen(pos, false);
            return true;
        });
    }

    private void tickBasicBedHomeState() {
        if (!this.isShelterNight()) {
            this.pendingBedHomePosition = null;
            this.pendingBedHomeApproachPosition = null;
            this.activeBedHomeApproachPosition = null;
            this.unreachableBedHomeRetries.clear();
            this.clearAutomaticBedHomePathFailures();
            this.clearNightCommunityAnchor();
            this.clearNightShelterWait();
            this.initialBedHomeSearchScheduled = false;
            return;
        }

        if (!this.homePositionSetByPlayer && this.homePositionIsAutomaticBed && !this.hasValidAutomaticBedHome()) {
            this.debugShelter("automatic bed cleared {} home={} reason=invalid",
                    this.getShelterDebugLabel(),
                    this.homePosition);
            this.homePosition = null;
            this.homePositionIsAutomaticBed = false;
            this.activeBedHomeApproachPosition = null;
            this.nextBedHomeSearchTick = this.tickCount;
        }
    }

    private boolean shouldKeepShelterDoorOpen(BlockPos pos) {
        Integer holdUntilTick = this.shelterDoorHoldUntilTicks.get(pos);
        if (holdUntilTick != null) {
            if (this.tickCount <= holdUntilTick) {
                return true;
            }

            this.shelterDoorHoldUntilTicks.remove(pos);
        }

        return !this.level().getEntitiesOfClass(
                RibbitEntity.class,
                new AABB(pos).inflate(SHELTER_DOOR_HOLD_DISTANCE, 1.0D, SHELTER_DOOR_HOLD_DISTANCE),
                ribbit -> ribbit.isAlive() && ribbit.shouldHoldShelterDoorOpen(pos)).isEmpty();
    }

    private boolean shouldHoldShelterDoorOpen(BlockPos doorPos) {
        if (!this.isCloseToShelterDoor(doorPos)) {
            return false;
        }

        if (this.isShelterNight() && this.hasUsableHomePosition()) {
            return true;
        }

        return this.horizontalCollision || this.isPathingThroughShelterDoor(doorPos);
    }

    private boolean isCloseToShelterDoor(BlockPos doorPos) {
        return this.getBoundingBox()
                .inflate(SHELTER_DOOR_HOLD_DISTANCE, 1.0D, SHELTER_DOOR_HOLD_DISTANCE)
                .intersects(new AABB(doorPos));
    }

    private boolean isPathingThroughShelterDoor(BlockPos doorPos) {
        Path path = this.getNavigation().getPath();
        if (path == null || path.isDone()) {
            return false;
        }

        int start = Math.max(0, path.getNextNodeIndex() - 1);
        int end = Math.min(path.getNextNodeIndex() + 2, path.getNodeCount() - 1);
        for (int i = start; i <= end; i++) {
            if (this.isShelterDoorPathNode(doorPos, path.getNode(i).asBlockPos())) {
                return true;
            }
        }

        return false;
    }

    private boolean isShelterDoorPathNode(BlockPos doorPos, BlockPos nodePos) {
        return doorPos.getX() == nodePos.getX()
                && doorPos.getZ() == nodePos.getZ()
                && Math.abs(doorPos.getY() - nodePos.getY()) <= 1;
    }

    private void setShelterDoorOpen(BlockPos pos, boolean open) {
        BlockState blockState = this.level().getBlockState(pos);
        if (!(blockState.getBlock() instanceof DoorBlock)) {
            return;
        }

        BlockPos doorPos = this.getLowerShelterDoorPos(pos, blockState);
        if (open && !this.canReachShelterDoor(doorPos)) {
            return;
        }

        BlockState doorState = this.level().getBlockState(doorPos);
        if (!(doorState.getBlock() instanceof DoorBlock doorBlock)
                || !doorBlock.type().canOpenByHand()
                || doorState.getValue(DoorBlock.POWERED)) {
            return;
        }

        if (open) {
            this.shelterDoorsToClose.add(doorPos.immutable());
            this.shelterDoorHoldUntilTicks.put(doorPos.immutable(), this.tickCount + SHELTER_DOOR_OPEN_HOLD_TICKS);
        }

        if (doorState.getValue(DoorBlock.OPEN) == open) {
            if (!open) {
                this.shelterDoorHoldUntilTicks.remove(doorPos);
            }
            return;
        }

        doorBlock.setOpen(this, this.level(), doorState, doorPos, open);

        if (!open) {
            this.shelterDoorHoldUntilTicks.remove(doorPos);
        }
    }

    private BlockPos getLowerShelterDoorPos(BlockPos pos, BlockState blockState) {
        return blockState.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
                ? pos.below()
                : pos.immutable();
    }

    private boolean canReachShelterDoor(BlockPos pos) {
        return this.getBoundingBox()
                .inflate(SHELTER_DOOR_REACH_DISTANCE, SHELTER_DOOR_REACH_DISTANCE, SHELTER_DOOR_REACH_DISTANCE)
                .intersects(new AABB(pos).expandTowards(0.0D, 1.0D, 0.0D));
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
                .map(ribbit -> new BedHomeSlotClaim(ribbit, ribbit.isAutomaticBedHomeRaw() && pos.equals(ribbit.homePosition)));
    }

    private String getBedHomeSlotClaimReason(BedHomeSlotClaim claim) {
        return "reserved_by=" + claim.ribbit().getShelterDebugOwnerLabel() + ":" + (claim.committedHome() ? "home" : "pending");
    }

    public static boolean isShelterDebugLoggingEnabled() {
        return SHELTER_DEBUG_LOGS;
    }

    public String getShelterDebugLabel() {
        return this.getShelterDebugOwnerLabel() + " pos=" + this.blockPosition();
    }

    private String getShelterDebugOwnerLabel() {
        String uuid = this.getStringUUID();
        String shortUuid = uuid.length() > 8 ? uuid.substring(0, 8) : uuid;
        return shortUuid + "/" + this.getRibbitData().getProfession().id().getPath();
    }

    public void logFishingSpotAbandoned(
            @Nullable BlockPos dryPos,
            @Nullable BlockPos waterPos,
            String reason,
            int ticksWithoutProgress,
            int nextRetryTick) {
        this.debugShelter(
                "fishing spot abandoned {} dry={} water={} reason={} ticksWithoutProgress={} nextRetryTick={} navDone={} inWater={} pos={}",
                this.getShelterDebugLabel(),
                dryPos,
                waterPos,
                reason,
                ticksWithoutProgress,
                nextRetryTick,
                this.getNavigation().isDone(),
                this.isInWater(),
                this.position());
    }

    private void debugShelter(String message, Object... params) {
        if (SHELTER_DEBUG_LOGS && !this.level().isClientSide()) {
            RibbitsCommon.LOGGER.info("[Ribbits bed home QA] " + message, params);
        }
    }

    private record BedHomeSlotDebug(BlockPos slot, String part, String reason) {
        private String toLogString() {
            return this.slot.toShortString() + "/" + this.part + "/" + this.reason;
        }
    }

    private record BedHomePathSelection(BlockPos slot, BlockPos approach) {
    }

    private record BedHomeSearchResult(Optional<BedHomePathSelection> selectedHome, int pendingClaims, int totalSlots) {
    }

    private static class BedHomePathRetry {
        private final RandomSource random;
        private int previousAttemptTick;
        private int nextScheduledAttemptTick;
        private int currentDelay;
        private int failuresWithoutProgress;
        private double bestDistanceSqr = Double.MAX_VALUE;
        private boolean blockedForNight;

        private BedHomePathRetry(RandomSource random, int tickCount) {
            this.random = random;
            this.previousAttemptTick = tickCount;
        }

        private boolean markAttempt(int tickCount, double distanceSqr) {
            this.previousAttemptTick = tickCount;
            if (distanceSqr < this.bestDistanceSqr - BED_HOME_PATH_PROGRESS_DISTANCE_SQR) {
                this.bestDistanceSqr = distanceSqr;
                this.failuresWithoutProgress = 0;
                this.currentDelay = 0;
            } else {
                this.failuresWithoutProgress++;
            }

            int delay = this.currentDelay
                    + this.random.nextInt(BED_HOME_PATH_RETRY_JITTER_TICKS)
                    + BED_HOME_PATH_RETRY_MIN_TICKS;
            this.currentDelay = Math.min(delay, BED_HOME_PATH_RETRY_MAX_TICKS);
            this.nextScheduledAttemptTick = tickCount + this.currentDelay;
            if (this.failuresWithoutProgress >= BED_HOME_MAX_PATH_FAILURES) {
                this.blockedForNight = true;
            }

            return this.blockedForNight;
        }

        private boolean isStillValid(int tickCount) {
            return tickCount - this.previousAttemptTick < BED_HOME_PATH_RETRY_MAX_TICKS;
        }

        private boolean shouldRetry(int tickCount) {
            return tickCount >= this.nextScheduledAttemptTick;
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
    }

    private record BedHomeSlotClaim(RibbitEntity ribbit, boolean committedHome) {
        private boolean pendingHome() {
            return !this.committedHome;
        }
    }

    public void reassessGoals() {
        if (this.level().isClientSide()) {
            return;
        }

        this.goalSelector.removeGoal(this.musicGoal);
        this.goalSelector.removeGoal(this.waterCropsGoal);
        this.goalSelector.removeGoal(this.fishGoal);
        this.goalSelector.removeGoal(this.applyBuffGoal);

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

    public boolean getBuffing() {
        return this.isBuffing;
    }

    public void setBuffing(boolean isBuffing) {
        this.entityData.set(BUFFING, isBuffing);
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
