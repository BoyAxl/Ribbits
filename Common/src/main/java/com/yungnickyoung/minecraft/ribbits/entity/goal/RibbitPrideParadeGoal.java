package com.yungnickyoung.minecraft.ribbits.entity.goal;

import com.yungnickyoung.minecraft.ribbits.entity.RibbitEntity;
import com.yungnickyoung.minecraft.ribbits.module.RibbitInstrumentModule;
import com.yungnickyoung.minecraft.ribbits.module.RibbitProfessionModule;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class RibbitPrideParadeGoal extends Goal {
    private static final Map<Level, PrideParadeSession> SESSIONS = new WeakHashMap<>();
    private static final Map<Level, Long> NEXT_PARADE_START_TICKS = new WeakHashMap<>();

    private static final int LEADER_CHECK_MIN_TICKS = 200;
    private static final int LEADER_CHECK_JITTER_TICKS = 200;
    private static final int LEADER_ROUTE_ATTEMPTS = 24;
    private static final int LEADER_FORWARD_WAYPOINT_ATTEMPTS = 24;
    private static final int LEADER_LATERAL_WAYPOINT_ATTEMPTS = 12;
    private static final int PARADE_WAYPOINT_MIN_DISTANCE = 5;
    private static final int PARADE_WAYPOINT_MAX_DISTANCE = 8;
    private static final int PARADE_MAX_ANCHOR_DISTANCE = 16;
    private static final int PARADE_MAX_FOLLOWERS = 10;
    private static final int PARADE_GATHER_MAX_TICKS = 20 * 60 * 2;
    private static final int PARADE_FAST_START_FOLLOWERS = 5;
    private static final int PARADE_MIN_GATHERED_FOLLOWERS = 2;
    private static final int PARADE_MAX_TICKS = 20 * 60 * 5;
    private static final int PARADE_SUCCESS_COOLDOWN_TICKS = 20 * 60 * 2;
    private static final int PARADE_FAILED_PATH_COOLDOWN_TICKS = 20 * 60;
    private static final int LEADER_WAYPOINT_FAILURE_LIMIT = 3;
    private static final int PATH_RECALCULATION_MIN_TICKS = 8;
    private static final int PATH_RECALCULATION_JITTER_TICKS = 8;
    private static final double START_CHANCE = 0.5D;
    private static final double JOIN_RANGE = 64.0D;
    private static final double LEADER_SPEED = 0.8D;
    private static final double GATHERING_FOLLOWER_SPEED = 1.5D;
    private static final double GATHERING_STOP_DISTANCE = 2.0D;
    private static final double FOLLOWER_CATCH_UP_SPEED = 1.2D;
    private static final double FOLLOWER_JOINED_SPEED = LEADER_SPEED;
    private static final double FOLLOWER_MIN_FORMATION_DISTANCE = 1.2D;
    private static final double FOLLOWER_MAX_FORMATION_DISTANCE = 2.0D;
    private static final double FOLLOWER_MIN_REMAIN_FORMATION_DISTANCE = 0.8D;
    private static final double FOLLOWER_MAX_REMAIN_FORMATION_DISTANCE = 3.0D;
    private static final double FOLLOWER_FULL_CATCH_UP_GAP = 3.0D;
    private static final double TARGET_REACHED_DISTANCE = 1.0D;
    private static final double LEADER_WAYPOINT_PROGRESS_DISTANCE_SQR = 0.25D;
    private static final double FOLLOWER_JOIN_PROGRESS_DISTANCE_SQR = 0.25D;
    private static final double LEADER_FORWARD_ARC_RADIANS = Math.PI / 2.0D;
    private static final double LEADER_LATERAL_ARC_RADIANS = Math.PI / 8.0D;
    private static final int LEADER_WAYPOINT_STUCK_TICKS = 200;
    private static final int FOLLOWER_JOIN_STUCK_TICKS = 200;

    private final RibbitEntity ribbit;

    @Nullable
    private PrideParadeSession activeSession;
    @Nullable
    private Path candidateLeaderPath;
    @Nullable
    private BlockPos candidateLeaderTarget;
    private boolean leader;
    private int nextLeaderCheckTick;
    private int ticksUntilNextPathRecalculation;

    public RibbitPrideParadeGoal(RibbitEntity ribbit) {
        this.ribbit = ribbit;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    public static boolean hasActiveParade(Level level) {
        return getActiveSession(level) != null;
    }

    public static void stopParadeFor(RibbitEntity ribbit) {
        PrideParadeSession session = SESSIONS.get(ribbit.level());
        if (session == null) {
            return;
        }

        if (session.leader == ribbit) {
            clearSession(ribbit.level(), session, "stopped_for_ribbit");
        } else {
            session.remove(ribbit);
        }
    }

    @Override
    public boolean canUse() {
        PrideParadeSession session = getActiveSession(this.ribbit.level());
        if (session != null) {
            return this.canJoinSession(session);
        }

        if (!this.ribbit.isDayActivityTime()) {
            return false;
        }

        Long nextParadeStartTick = NEXT_PARADE_START_TICKS.get(this.ribbit.level());
        if (nextParadeStartTick != null && this.ribbit.level().getGameTime() < nextParadeStartTick) {
            return false;
        }

        if (this.ribbit.tickCount < this.nextLeaderCheckTick) {
            return false;
        }

        this.nextLeaderCheckTick = this.ribbit.tickCount
                + LEADER_CHECK_MIN_TICKS
                + this.ribbit.getRandom().nextInt(LEADER_CHECK_JITTER_TICKS + 1);

        String leaderStartBlocker = this.getLeaderStartBlocker();
        if (leaderStartBlocker != null) {
            return false;
        }

        if (this.ribbit.getRandom().nextDouble() > START_CHANCE) {
            return false;
        }

        Route route = this.findLeaderRoute();
        if (route == null) {
            return false;
        }

        this.leader = true;
        this.candidateLeaderTarget = route.target();
        this.candidateLeaderPath = route.path();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        PrideParadeSession session = getActiveSession(this.ribbit.level());
        if (session == null || session != this.activeSession) {
            return false;
        }

        if (session.leader == this.ribbit) {
            return session.getInvalidReason() == null;
        }

        return this.getRemainRejectionReason(session) == null;
    }

    @Override
    public void start() {
        this.ticksUntilNextPathRecalculation = 0;

        if (this.leader) {
            PrideParadeSession existingSession = getActiveSession(this.ribbit.level());
            if (existingSession != null) {
                this.activeSession = null;
                this.candidateLeaderPath = null;
                this.candidateLeaderTarget = null;
                this.leader = false;
                this.ribbit.getNavigation().stop();
                return;
            }

            this.activeSession = new PrideParadeSession(this.ribbit, this.candidateLeaderTarget);
            SESSIONS.put(this.ribbit.level(), this.activeSession);
            this.ribbit.getNavigation().stop();
        } else {
            this.activeSession = getActiveSession(this.ribbit.level());
            if (this.activeSession != null) {
                String rejectionReason = this.getJoinRejectionReason(this.activeSession);
                if (rejectionReason != null) {
                    this.activeSession = null;
                    this.ribbit.getNavigation().stop();
                    return;
                }

                if (this.activeSession.hasStartedWalking()) {
                    this.activeSession.add(this.ribbit);
                } else {
                    this.activeSession.addGatheringCandidate(this.ribbit);
                }
            }
        }
    }

    @Override
    public void stop() {
        if (this.activeSession != null) {
            if (this.activeSession.leader == this.ribbit) {
                clearSession(this.ribbit.level(), this.activeSession, "goal_stopped");
            } else {
                this.activeSession.remove(this.ribbit);
            }
        }

        this.ribbit.getNavigation().stop();
        this.activeSession = null;
        this.candidateLeaderPath = null;
        this.candidateLeaderTarget = null;
        this.leader = false;
        this.ticksUntilNextPathRecalculation = 0;
    }

    public void resetTarget() {
        stopParadeFor(this.ribbit);
        this.activeSession = null;
        this.candidateLeaderPath = null;
        this.candidateLeaderTarget = null;
        this.leader = false;
        this.ticksUntilNextPathRecalculation = 0;
    }

    private void closeActiveSession(String reason) {
        if (this.activeSession == null) {
            return;
        }

        clearSession(this.ribbit.level(), this.activeSession, reason);
        this.activeSession = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean isInterruptable() {
        return true;
    }

    @Override
    public void tick() {
        if (this.activeSession == null) {
            return;
        }

        this.activeSession.prune();
        String invalidReason = this.activeSession.getInvalidReason();
        if (invalidReason != null) {
            this.closeActiveSession(invalidReason);
            return;
        }

        if (this.activeSession.leader == this.ribbit) {
            this.tickLeader();
        } else {
            this.tickFollower();
        }
    }

    private void tickLeader() {
        if (this.activeSession == null || this.activeSession.target == null) {
            return;
        }

        this.ribbit.getNavigation().setSpeedModifier(this.getWaterAdjustedSpeed(LEADER_SPEED));
        if (!this.activeSession.hasStartedWalking()) {
            if (this.activeSession.shouldGiveUpGathering()) {
                this.closeActiveSession("not_enough_gathered");
                return;
            }

            if (!this.activeSession.shouldStartWalking()) {
                this.ribbit.getNavigation().stop();
                return;
            }

            this.activeSession.keepOnlyGatheredFollowersForWalking();
            this.activeSession.clearGatheringCandidates();
            int gatheredFollowers = this.activeSession.countGatheredFollowers();
            if (gatheredFollowers < PARADE_MIN_GATHERED_FOLLOWERS) {
                this.closeActiveSession("not_enough_gathered_after_consolidation");
                return;
            }

            this.activeSession.markStartedWalking(this.ribbit.tickCount + PARADE_MAX_TICKS);
            this.moveAlongLeaderPath();
            return;
        }

        this.ticksUntilNextPathRecalculation = Math.max(this.ticksUntilNextPathRecalculation - 1, 0);
        if (this.ticksUntilNextPathRecalculation > 0) {
            return;
        }

        if (this.ribbit.distanceToSqr(Vec3.atCenterOf(this.activeSession.target)) <= TARGET_REACHED_DISTANCE * TARGET_REACHED_DISTANCE) {
            Route nextRoute = this.findNextLeaderWaypointRoute();
            if (nextRoute == null) {
                if (this.activeSession.recordLeaderWaypointFailure()) {
                    this.closeActiveSession("no_forward_waypoint");
                    return;
                }

                this.ribbit.getNavigation().stop();
                this.resetPathRecalculationDelay();
                return;
            }

            this.activeSession.clearLeaderWaypointFailures();
            this.activeSession.setTarget(nextRoute.target(), this.ribbit.blockPosition());
            this.ribbit.getNavigation().moveTo(nextRoute.path(), this.getWaterAdjustedSpeed(LEADER_SPEED));
            this.resetPathRecalculationDelay();
            return;
        }

        if (this.activeSession.isLeaderWaypointProgressStalled(this.ribbit.tickCount)) {
            Route nextRoute = this.findNextLeaderWaypointRoute();
            if (nextRoute == null) {
                if (this.activeSession.recordLeaderWaypointFailure()) {
                    this.closeActiveSession("leader_stuck_no_waypoint");
                    return;
                }

                this.ribbit.getNavigation().stop();
                this.resetPathRecalculationDelay();
                return;
            }

            this.activeSession.clearLeaderWaypointFailures();
            this.activeSession.setTarget(nextRoute.target(), this.ribbit.blockPosition());
            this.ribbit.getNavigation().moveTo(nextRoute.path(), this.getWaterAdjustedSpeed(LEADER_SPEED));
            this.resetPathRecalculationDelay();
            return;
        }

        Path path = this.ribbit.getNavigation().createPath(this.activeSession.target, 0);
        if (path == null || !path.canReach()) {
            Route nextRoute = this.findNextLeaderWaypointRoute();
            if (nextRoute == null) {
                if (this.activeSession.recordLeaderWaypointFailure()) {
                    this.closeActiveSession("path_failed_no_waypoint");
                    return;
                }

                this.ribbit.getNavigation().stop();
                this.resetPathRecalculationDelay();
                return;
            }

            this.activeSession.clearLeaderWaypointFailures();
            this.activeSession.setTarget(nextRoute.target(), this.ribbit.blockPosition());
            this.ribbit.getNavigation().moveTo(nextRoute.path(), this.getWaterAdjustedSpeed(LEADER_SPEED));
            this.resetPathRecalculationDelay();
            return;
        }

        this.activeSession.clearLeaderWaypointFailures();
        this.ribbit.getNavigation().moveTo(path, this.getWaterAdjustedSpeed(LEADER_SPEED));
        this.resetPathRecalculationDelay();
    }

    private void tickFollower() {
        if (this.activeSession == null) {
            return;
        }

        if (!this.activeSession.hasStartedWalking()) {
            this.tickGatheringFollower();
            return;
        }

        if (!this.activeSession.isFormed(this.ribbit) && !this.activeSession.isActiveJoiningMember(this.ribbit)) {
            this.ribbit.getNavigation().stop();
            return;
        }

        RibbitEntity target = this.activeSession.getFormationTargetFor(this.ribbit);
        if (target == null || target.isRemoved() || target.isDeadOrDying()) {
            return;
        }

        this.ribbit.getLookControl().setLookAt(target, 20.0F, 20.0F);
        double distance = this.ribbit.distanceTo(target);
        if (distance < FOLLOWER_MIN_FORMATION_DISTANCE) {
            this.activeSession.clearJoiningProgress(this.ribbit);
            this.ribbit.getNavigation().stop();
            return;
        }

        boolean formed = this.activeSession.isFormed(this.ribbit);
        if (!formed && distance <= FOLLOWER_MAX_FORMATION_DISTANCE) {
            this.activeSession.markFormed(this.ribbit);
            this.ribbit.getNavigation().stop();
            return;
        }

        if (!formed && this.activeSession.releaseIfJoiningProgressStalled(this.ribbit, target)) {
            this.ribbit.getNavigation().stop();
            this.activeSession = null;
            return;
        }

        double speed = this.getWaterAdjustedSpeed(formed ? getFormedFollowerSpeed(distance) : FOLLOWER_CATCH_UP_SPEED);
        this.ribbit.getNavigation().setSpeedModifier(speed);

        this.ticksUntilNextPathRecalculation = Math.max(this.ticksUntilNextPathRecalculation - 1, 0);
        if (this.ticksUntilNextPathRecalculation > 0) {
            return;
        }

        Path path = this.ribbit.getNavigation().createPath(target, 0);
        if (path == null) {
            this.ribbit.getNavigation().stop();
            this.resetPathRecalculationDelay();
            return;
        }

        this.ribbit.getNavigation().moveTo(path, speed);
        this.resetPathRecalculationDelay();
    }

    private void tickGatheringFollower() {
        if (this.activeSession == null) {
            return;
        }

        RibbitEntity target = this.activeSession.leader;
        if (target == null || target.isRemoved() || target.isDeadOrDying()) {
            return;
        }

        this.ribbit.getLookControl().setLookAt(target, 20.0F, 20.0F);
        if (this.ribbit.distanceTo(target) <= GATHERING_STOP_DISTANCE) {
            this.activeSession.promoteGatheringCandidate(this.ribbit);
            this.activeSession.clearJoiningProgress(this.ribbit);
            this.ribbit.getNavigation().stop();
            return;
        }

        if (this.activeSession.isGatheringCandidate(this.ribbit)
                && this.activeSession.releaseIfJoiningProgressStalled(this.ribbit, target)) {
            this.ribbit.getNavigation().stop();
            this.activeSession = null;
            return;
        }

        this.ticksUntilNextPathRecalculation = Math.max(this.ticksUntilNextPathRecalculation - 1, 0);
        if (this.ticksUntilNextPathRecalculation > 0) {
            return;
        }

        double speed = this.getWaterAdjustedSpeed(GATHERING_FOLLOWER_SPEED);
        this.ribbit.getNavigation().setSpeedModifier(speed);
        if (!this.ribbit.getNavigation().moveTo(target, speed)) {
            this.ribbit.getNavigation().stop();
            this.resetPathRecalculationDelay();
            return;
        }

        this.resetPathRecalculationDelay();
    }

    private static double getFormedFollowerSpeed(double distance) {
        double gap = Math.max(distance - FOLLOWER_MIN_FORMATION_DISTANCE, 0.0D);
        double catchUpFactor = Math.min(gap / FOLLOWER_FULL_CATCH_UP_GAP, 1.0D);
        return FOLLOWER_JOINED_SPEED + (FOLLOWER_CATCH_UP_SPEED - FOLLOWER_JOINED_SPEED) * catchUpFactor;
    }

    private double getWaterAdjustedSpeed(double speed) {
        return this.ribbit.isInWater() ? speed * RibbitEntity.WATER_SPEED_MULTIPLIER : speed;
    }

    private void moveAlongLeaderPath() {
        if (this.candidateLeaderPath != null) {
            this.ribbit.getNavigation().moveTo(this.candidateLeaderPath, this.getWaterAdjustedSpeed(LEADER_SPEED));
            this.resetPathRecalculationDelay();
        }
    }

    @Nullable
    private String getLeaderStartBlocker() {
        if (!this.ribbit.isPrideRibbit()) {
            return "not_pride";
        }
        if (!this.ribbit.getRibbitData().getProfession().equals(RibbitProfessionModule.NITWIT)) {
            return "not_nitwit";
        }

        String participationBlocker = getParticipationBlocker(this.ribbit);
        if (participationBlocker != null) {
            return participationBlocker;
        }
        if (this.ribbit.isInWater()) {
            return "leader_in_water";
        }
        if (!this.ribbit.isStableOutdoorActivityPosition()) {
            return "not_stable_outdoor";
        }

        return null;
    }

    private boolean canJoinSession(PrideParadeSession session) {
        return this.getJoinRejectionReason(session) == null;
    }

    private static boolean isValidParadeParticipantPosition(RibbitEntity ribbit) {
        return ribbit.isStableOutdoorActivityPosition() || isOpenParadeWater(ribbit, ribbit.blockPosition());
    }

    @Nullable
    private String getJoinRejectionReason(PrideParadeSession session) {
        double distanceToLeaderSqr = this.ribbit.distanceToSqr(session.leader);
        if (distanceToLeaderSqr > JOIN_RANGE * JOIN_RANGE) {
            return "outside_join_range";
        }

        String participationBlocker = getParticipationBlocker(this.ribbit);
        if (participationBlocker != null) {
            return participationBlocker;
        }

        if (!isValidParadeParticipantPosition(this.ribbit)) {
            return "not_valid_parade_position";
        }

        if (session.isFull()) {
            return "session_full";
        }

        if (session.hasMember(this.ribbit)) {
            return "already_member";
        }

        if (session.isGatheringCandidate(this.ribbit)) {
            return "already_invited";
        }

        if (session.wasReleasedForNoProgress(this.ribbit)) {
            return "released_no_progress";
        }

        if (!session.hasStartedWalking() && session.isGatheringFull()) {
            return "gathering_full";
        }

        if (!session.canAcceptNewFollower()) {
            return session.hasStartedWalking() ? "waiting_for_current_member_to_form" : "gathering_full";
        }

        if (!session.isNextJoinCandidate(this.ribbit)) {
            return "not_nearest_candidate";
        }

        if (session.leader == this.ribbit) {
            return "is_leader";
        }

        return null;
    }

    @Nullable
    private String getRemainRejectionReason(PrideParadeSession session) {
        String participationBlocker = getParticipationBlocker(this.ribbit);
        if (participationBlocker != null) {
            return participationBlocker;
        }

        if (!session.hasMember(this.ribbit)
                && (session.hasStartedWalking() || !session.isGatheringCandidate(this.ribbit))) {
            return "not_session_member";
        }

        if (this.ribbit.distanceToSqr(session.leader) > JOIN_RANGE * JOIN_RANGE) {
            return "outside_join_range";
        }

        return null;
    }

    @Nullable
    private static String getParticipationBlocker(RibbitEntity ribbit) {
        if (!ribbit.isDayActivityTime()) {
            return "not_day_activity_time";
        }
        if (ribbit.isAutonomousAiPaused()) {
            return "autonomous_ai_paused";
        }
        if (ribbit.isInRain()) {
            return "in_rain";
        }
        if (ribbit.isUmbrellaFalling()) {
            return "umbrella_falling";
        }
        if (ribbit.getPlayingInstrument()) {
            return "playing_instrument";
        }
        if (ribbit.getResting()) {
            return "resting";
        }
        if (ribbit.getFishing()) {
            return "fishing";
        }
        if (ribbit.getWatering()) {
            return "watering";
        }
        if (ribbit.getBuffing()) {
            return "buffing";
        }
        if (ribbit.getLastHurtByMob() != null) {
            return "has_hurt_target";
        }
        if (ribbit.isFreezing()) {
            return "freezing";
        }
        if (ribbit.isOnFire()) {
            return "on_fire";
        }
        if (ribbit.isDeadOrDying()) {
            return "dead_or_dying";
        }

        return null;
    }

    @Nullable
    private Route findLeaderRoute() {
        for (int attempt = 0; attempt < LEADER_ROUTE_ATTEMPTS; attempt++) {
            BlockPos target = this.findRandomCandidateTarget();
            if (target == null) {
                continue;
            }

            Path path = this.ribbit.getNavigation().createPath(target, 0);
            if (path != null && path.canReach()) {
                return new Route(target, path);
            }
        }

        return null;
    }

    @Nullable
    private Route findNextLeaderWaypointRoute() {
        if (this.activeSession == null) {
            return null;
        }

        Route forwardRoute = this.findForwardWaypointRoute(this.activeSession.travelDirection);
        if (forwardRoute != null) {
            return forwardRoute;
        }

        return this.findLateralWaypointRoute(this.activeSession.travelDirection);
    }

    @Nullable
    private Route findForwardWaypointRoute(Vec3 forward) {
        double baseAngle = Math.atan2(forward.z, forward.x);
        for (int attempt = 0; attempt < LEADER_FORWARD_WAYPOINT_ATTEMPTS; attempt++) {
            double offset = (this.ribbit.getRandom().nextDouble() - this.ribbit.getRandom().nextDouble())
                    * LEADER_FORWARD_ARC_RADIANS;
            Route route = this.findWaypointRoute(baseAngle + offset);
            if (route != null) {
                return route;
            }
        }

        return null;
    }

    @Nullable
    private Route findLateralWaypointRoute(Vec3 forward) {
        double baseAngle = Math.atan2(forward.z, forward.x);
        for (int attempt = 0; attempt < LEADER_LATERAL_WAYPOINT_ATTEMPTS; attempt++) {
            double side = attempt % 2 == 0 ? 1.0D : -1.0D;
            double lateralOffset = Math.PI / 2.0D - this.ribbit.getRandom().nextDouble() * LEADER_LATERAL_ARC_RADIANS;
            Route route = this.findWaypointRoute(baseAngle + side * lateralOffset);
            if (route != null) {
                return route;
            }
        }

        return null;
    }

    @Nullable
    private Route findWaypointRoute(double angle) {
        int distance = PARADE_WAYPOINT_MIN_DISTANCE
                + this.ribbit.getRandom().nextInt(PARADE_WAYPOINT_MAX_DISTANCE - PARADE_WAYPOINT_MIN_DISTANCE + 1);
        BlockPos target = this.findCandidateTarget(angle, distance);
        if (target == null) {
            return null;
        }

        Path path = this.ribbit.getNavigation().createPath(target, 0);
        if (path != null && path.canReach()) {
            return new Route(target, path);
        }

        return null;
    }

    @Nullable
    private BlockPos findRandomCandidateTarget() {
        double angle = this.ribbit.getRandom().nextDouble() * Math.PI * 2.0D;
        int distance = PARADE_WAYPOINT_MIN_DISTANCE
                + this.ribbit.getRandom().nextInt(PARADE_WAYPOINT_MAX_DISTANCE - PARADE_WAYPOINT_MIN_DISTANCE + 1);
        return this.findCandidateTarget(angle, distance);
    }

    @Nullable
    private BlockPos findCandidateTarget(double angle, int distance) {
        int targetX = this.ribbit.blockPosition().getX() + floorOffset(Math.cos(angle) * distance);
        int targetZ = this.ribbit.blockPosition().getZ() + floorOffset(Math.sin(angle) * distance);
        int baseY = this.ribbit.blockPosition().getY();

        for (int yOffset = 4; yOffset >= -8; yOffset--) {
            BlockPos pos = new BlockPos(targetX, baseY + yOffset, targetZ);
            if (this.isValidParadeTarget(pos)) {
                return pos;
            }
        }

        return null;
    }

    private boolean isValidParadeTarget(BlockPos pos) {
        if (!this.isWithinParadeAnchorRange(pos)
                || !this.hasEmptyCollision(pos)
                || !this.hasEmptyCollision(pos.above())) {
            return false;
        }

        return this.ribbit.isStableOutdoorActivityPosition(pos) || this.isOpenParadeWater(pos);
    }

    private boolean isWithinParadeAnchorRange(BlockPos pos) {
        BlockPos anchor = this.ribbit.getStrollAnchorPosition();
        double x = pos.getX() - anchor.getX();
        double z = pos.getZ() - anchor.getZ();
        return x * x + z * z <= PARADE_MAX_ANCHOR_DISTANCE * PARADE_MAX_ANCHOR_DISTANCE;
    }

    private boolean isOpenParadeWater(BlockPos pos) {
        return isOpenParadeWater(this.ribbit, pos);
    }

    private static boolean isOpenParadeWater(RibbitEntity ribbit, BlockPos pos) {
        return ribbit.level().getFluidState(pos).is(FluidTags.WATER)
                && hasEmptyCollision(ribbit, pos.above());
    }

    private boolean hasEmptyCollision(BlockPos pos) {
        return hasEmptyCollision(this.ribbit, pos);
    }

    private static boolean hasEmptyCollision(RibbitEntity ribbit, BlockPos pos) {
        return ribbit.level().getBlockState(pos).getCollisionShape(ribbit.level(), pos).isEmpty();
    }

    private void resetPathRecalculationDelay() {
        this.ticksUntilNextPathRecalculation = PATH_RECALCULATION_MIN_TICKS
                + this.ribbit.getRandom().nextInt(PATH_RECALCULATION_JITTER_TICKS + 1);
    }

    private static int floorOffset(double value) {
        return (int) Math.floor(value);
    }

    @Nullable
    private static PrideParadeSession getActiveSession(Level level) {
        PrideParadeSession session = SESSIONS.get(level);
        if (session == null) {
            return null;
        }

        session.prune();
        String invalidReason = session.getInvalidReason();
        if (invalidReason != null) {
            clearSession(level, session, invalidReason);
            return null;
        }

        return session;
    }

    private static void clearSession(Level level, PrideParadeSession session, String reason) {
        if (SESSIONS.get(level) != session) {
            return;
        }

        int cooldownTicks = getCooldownTicksForCloseReason(reason);
        if (cooldownTicks > 0) {
            NEXT_PARADE_START_TICKS.put(level, level.getGameTime() + cooldownTicks);
        }

        session.members.forEach(member -> member.getNavigation().stop());
        if (session.leader != null) {
            session.leader.getNavigation().stop();
        }
        SESSIONS.remove(level);
    }

    private static int getCooldownTicksForCloseReason(String reason) {
        if ("duration_completed".equals(reason) || "no_forward_waypoint".equals(reason)) {
            return PARADE_SUCCESS_COOLDOWN_TICKS;
        }
        if ("path_failed_no_waypoint".equals(reason) || "leader_stuck_no_waypoint".equals(reason)) {
            return PARADE_FAILED_PATH_COOLDOWN_TICKS;
        }

        return 0;
    }

    private record Route(BlockPos target, Path path) {
    }

    private static class PrideParadeSession {
        private RibbitEntity leader;
        @Nullable
        private BlockPos target;
        private final int createdAtTick;
        private final List<RibbitEntity> members = new ArrayList<>();
        private final Set<RibbitEntity> gatheringCandidates = new HashSet<>();
        private final Set<RibbitEntity> formedMembers = new HashSet<>();
        private final Set<RibbitEntity> releasedNoProgressMembers = new HashSet<>();
        private final Map<RibbitEntity, JoiningProgress> joiningProgress = new HashMap<>();
        private final WaypointProgress leaderWaypointProgress = new WaypointProgress();
        private Vec3 travelDirection = new Vec3(1.0D, 0.0D, 0.0D);
        private boolean startedWalking;
        private int expiresAtTick;
        private int leaderWaypointFailures;
        @Nullable
        private RibbitEntity cachedNextJoinCandidate;
        private long cachedNextJoinCandidateGameTime = Long.MIN_VALUE;

        private PrideParadeSession(RibbitEntity leader, @Nullable BlockPos target) {
            this.leader = leader;
            this.createdAtTick = leader.tickCount;
            if (target != null) {
                this.setTarget(target, leader.blockPosition());
            }
        }

        private boolean isValid() {
            return this.getInvalidReason() == null;
        }

        @Nullable
        private String getInvalidReason() {
            if (this.leader == null || this.leader.isRemoved() || this.leader.isDeadOrDying()) {
                return "invalid_leader";
            }
            if (!this.leader.isDayActivityTime()) {
                return "not_day_activity_time";
            }
            if (this.leader.isAutonomousAiPaused()) {
                return "autonomous_ai_paused";
            }
            if (!this.startedWalking && this.leader.isInWater()) {
                return "leader_in_water";
            }
            if (this.leader.isInRain()) {
                return "leader_in_rain";
            }
            if (this.leader.isUmbrellaFalling()) {
                return "leader_umbrella_falling";
            }
            if (this.target == null) {
                return "missing_target";
            }
            if (this.startedWalking && this.leader.tickCount > this.expiresAtTick) {
                return "duration_completed";
            }

            return null;
        }

        private boolean isFull() {
            return this.members.size() >= PARADE_MAX_FOLLOWERS;
        }

        private boolean isGatheringFull() {
            return this.members.size() + this.gatheringCandidates.size() >= PARADE_MAX_FOLLOWERS;
        }

        private boolean hasMember(RibbitEntity ribbit) {
            return this.members.contains(ribbit);
        }

        private boolean isGatheringCandidate(RibbitEntity ribbit) {
            return this.gatheringCandidates.contains(ribbit);
        }

        private boolean hasStartedWalking() {
            return this.startedWalking;
        }

        private void markStartedWalking(int expiresAtTick) {
            this.startedWalking = true;
            this.expiresAtTick = expiresAtTick;
            this.clearLeaderWaypointFailures();
            this.resetLeaderWaypointProgress();
        }

        private void setTarget(BlockPos target, BlockPos from) {
            this.target = target;

            Vec3 direction = Vec3.atCenterOf(target).subtract(Vec3.atCenterOf(from));
            double horizontalLength = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
            if (horizontalLength > 1.0E-4D) {
                this.travelDirection = new Vec3(direction.x / horizontalLength, 0.0D, direction.z / horizontalLength);
            }
            this.leaderWaypointProgress.reset(
                    target,
                    this.leader.distanceToSqr(Vec3.atCenterOf(target)),
                    this.leader.tickCount);
        }

        private boolean recordLeaderWaypointFailure() {
            this.leaderWaypointFailures++;
            return this.leaderWaypointFailures >= LEADER_WAYPOINT_FAILURE_LIMIT;
        }

        private void clearLeaderWaypointFailures() {
            this.leaderWaypointFailures = 0;
        }

        private void resetLeaderWaypointProgress() {
            if (this.target == null || this.leader == null) {
                return;
            }

            this.leaderWaypointProgress.reset(
                    this.target,
                    this.leader.distanceToSqr(Vec3.atCenterOf(this.target)),
                    this.leader.tickCount);
        }

        private boolean isLeaderWaypointProgressStalled(int tickCount) {
            if (this.target == null || this.leader == null) {
                return false;
            }

            return this.leaderWaypointProgress.isStalled(
                    this.target,
                    this.leader.distanceToSqr(Vec3.atCenterOf(this.target)),
                    tickCount);
        }

        private boolean shouldStartWalking() {
            if (this.startedWalking) {
                return false;
            }

            int gatheredFollowers = this.countGatheredFollowers();
            if (gatheredFollowers >= PARADE_FAST_START_FOLLOWERS) {
                return true;
            }

            return this.leader.tickCount - this.createdAtTick >= PARADE_GATHER_MAX_TICKS
                    && gatheredFollowers >= PARADE_MIN_GATHERED_FOLLOWERS;
        }

        private boolean shouldGiveUpGathering() {
            return !this.startedWalking
                    && this.leader.tickCount - this.createdAtTick >= PARADE_GATHER_MAX_TICKS
                    && this.countGatheredFollowers() < PARADE_MIN_GATHERED_FOLLOWERS;
        }

        private int countGatheredFollowers() {
            int gatheredFollowers = 0;
            double maxDistanceSqr = GATHERING_STOP_DISTANCE * GATHERING_STOP_DISTANCE;
            for (RibbitEntity member : this.members) {
                if (member.distanceToSqr(this.leader) <= maxDistanceSqr) {
                    gatheredFollowers++;
                }
            }

            return gatheredFollowers;
        }

        private void keepOnlyGatheredFollowersForWalking() {
            double maxDistanceSqr = GATHERING_STOP_DISTANCE * GATHERING_STOP_DISTANCE;
            boolean changed = false;
            Iterator<RibbitEntity> iterator = this.members.iterator();
            while (iterator.hasNext()) {
                RibbitEntity member = iterator.next();
                if (member.distanceToSqr(this.leader) <= maxDistanceSqr) {
                    continue;
                }

                member.getNavigation().stop();
                iterator.remove();
                this.formedMembers.remove(member);
                this.joiningProgress.remove(member);
                changed = true;
            }
            if (changed) {
                this.invalidateNextJoinCandidateCache();
            }
        }

        private void clearGatheringCandidates() {
            boolean changed = !this.gatheringCandidates.isEmpty();
            this.gatheringCandidates.forEach(candidate -> {
                candidate.getNavigation().stop();
                this.joiningProgress.remove(candidate);
            });
            this.gatheringCandidates.clear();
            if (changed) {
                this.invalidateNextJoinCandidateCache();
            }
        }

        private boolean canAcceptNewFollower() {
            if (!this.startedWalking) {
                return !this.isGatheringFull();
            }

            if (this.members.isEmpty()) {
                return true;
            }

            if (!this.formedMembers.containsAll(this.members)) {
                return false;
            }

            for (RibbitEntity member : this.members) {
                if (!this.isCurrentlyCloseEnoughForFormation(member)) {
                    return false;
                }
            }

            return true;
        }

        private boolean isNextJoinCandidate(RibbitEntity ribbit) {
            RibbitEntity nextCandidate = this.getNextJoinCandidate();
            return nextCandidate == ribbit;
        }

        @Nullable
        private RibbitEntity getNextJoinCandidate() {
            long gameTime = this.leader.level().getGameTime();
            if (this.cachedNextJoinCandidateGameTime == gameTime) {
                return this.cachedNextJoinCandidate;
            }

            RibbitEntity joinAnchor = this.getNextJoinAnchor();
            RibbitEntity closest = null;
            double closestDistanceSqr = Double.MAX_VALUE;

            for (RibbitEntity nearby : this.leader.level()
                    .getEntitiesOfClass(RibbitEntity.class, this.leader.getBoundingBox().inflate(JOIN_RANGE, 6.0D, JOIN_RANGE))) {
                if (nearby == this.leader
                        || this.hasMember(nearby)
                        || this.isGatheringCandidate(nearby)
                        || this.releasedNoProgressMembers.contains(nearby)
                        || getParticipationBlocker(nearby) != null
                        || !isValidParadeParticipantPosition(nearby)
                        || nearby.distanceToSqr(this.leader) > JOIN_RANGE * JOIN_RANGE) {
                    continue;
                }

                double distanceSqr = nearby.distanceToSqr(joinAnchor);
                if (distanceSqr < closestDistanceSqr) {
                    closest = nearby;
                    closestDistanceSqr = distanceSqr;
                }
            }

            this.cachedNextJoinCandidate = closest;
            this.cachedNextJoinCandidateGameTime = gameTime;
            return closest;
        }

        private void invalidateNextJoinCandidateCache() {
            this.cachedNextJoinCandidate = null;
            this.cachedNextJoinCandidateGameTime = Long.MIN_VALUE;
        }

        private RibbitEntity getNextJoinAnchor() {
            if (!this.startedWalking) {
                return this.leader;
            }

            for (int i = this.members.size() - 1; i >= 0; i--) {
                RibbitEntity member = this.members.get(i);
                if (this.formedMembers.contains(member)) {
                    return member;
                }
            }

            return this.leader;
        }

        private boolean isCurrentlyCloseEnoughForFormation(RibbitEntity member) {
            RibbitEntity target = this.getFormationTargetFor(member);
            if (target == null || target.isRemoved() || target.isDeadOrDying()) {
                return false;
            }

            double distance = member.distanceTo(target);
            return distance >= FOLLOWER_MIN_REMAIN_FORMATION_DISTANCE
                    && distance <= FOLLOWER_MAX_REMAIN_FORMATION_DISTANCE;
        }

        private void add(RibbitEntity ribbit) {
            if (!this.hasMember(ribbit) && !this.isFull()) {
                ribbit.setPlayingInstrument(false);
                ribbit.setInstrument(RibbitInstrumentModule.NONE);
                this.members.add(ribbit);
                this.invalidateNextJoinCandidateCache();
            }
        }

        private void addGatheringCandidate(RibbitEntity ribbit) {
            if (this.isGatheringFull()
                    || this.hasMember(ribbit)
                    || this.gatheringCandidates.contains(ribbit)
                    || this.releasedNoProgressMembers.contains(ribbit)) {
                return;
            }

            ribbit.setPlayingInstrument(false);
            ribbit.setInstrument(RibbitInstrumentModule.NONE);
            this.gatheringCandidates.add(ribbit);
            this.invalidateNextJoinCandidateCache();
        }

        private void promoteGatheringCandidate(RibbitEntity ribbit) {
            if (!this.gatheringCandidates.remove(ribbit)
                    || this.hasMember(ribbit)
                    || this.isFull()) {
                return;
            }

            this.joiningProgress.remove(ribbit);
            this.members.add(ribbit);
            this.invalidateNextJoinCandidateCache();
        }

        private void remove(RibbitEntity ribbit) {
            boolean changed = this.members.remove(ribbit);
            changed |= this.gatheringCandidates.remove(ribbit);
            changed |= this.formedMembers.remove(ribbit);
            changed |= this.joiningProgress.remove(ribbit) != null;
            if (changed) {
                this.invalidateNextJoinCandidateCache();
            }
        }

        @Nullable
        private RibbitEntity getFormationTargetFor(RibbitEntity ribbit) {
            if (!this.startedWalking && !this.formedMembers.contains(ribbit)) {
                return this.leader;
            }

            int index = this.members.indexOf(ribbit);
            if (index < 0) {
                return null;
            }

            for (int i = index - 1; i >= 0; i--) {
                RibbitEntity member = this.members.get(i);
                if (this.formedMembers.contains(member)) {
                    return member;
                }
            }

            return this.leader;
        }

        private boolean isActiveJoiningMember(RibbitEntity ribbit) {
            if (!this.startedWalking || this.formedMembers.contains(ribbit)) {
                return false;
            }

            for (RibbitEntity member : this.members) {
                if (!this.formedMembers.contains(member)) {
                    return member == ribbit;
                }
            }

            return false;
        }

        private boolean isFormed(RibbitEntity ribbit) {
            return this.formedMembers.contains(ribbit);
        }

        private void markFormed(RibbitEntity ribbit) {
            if (!this.hasMember(ribbit)) {
                return;
            }

            this.formedMembers.add(ribbit);
            this.joiningProgress.remove(ribbit);
            this.invalidateNextJoinCandidateCache();
        }

        private boolean wasReleasedForNoProgress(RibbitEntity ribbit) {
            return this.releasedNoProgressMembers.contains(ribbit);
        }

        private void clearJoiningProgress(RibbitEntity ribbit) {
            this.joiningProgress.remove(ribbit);
        }

        private boolean releaseIfJoiningProgressStalled(RibbitEntity ribbit, RibbitEntity target) {
            JoiningProgress progress = this.joiningProgress.computeIfAbsent(ribbit, ignored -> new JoiningProgress(target, ribbit.distanceToSqr(target)));
            if (progress.target != target) {
                progress.reset(target, ribbit.distanceToSqr(target));
                return false;
            }

            double distanceSqr = ribbit.distanceToSqr(target);
            if (distanceSqr < progress.bestDistanceSqr - FOLLOWER_JOIN_PROGRESS_DISTANCE_SQR) {
                progress.bestDistanceSqr = distanceSqr;
                progress.ticksWithoutProgress = 0;
                return false;
            }

            progress.ticksWithoutProgress++;
            if (progress.ticksWithoutProgress < FOLLOWER_JOIN_STUCK_TICKS) {
                return false;
            }

            this.members.remove(ribbit);
            this.gatheringCandidates.remove(ribbit);
            this.formedMembers.remove(ribbit);
            this.joiningProgress.remove(ribbit);
            this.releasedNoProgressMembers.add(ribbit);
            this.invalidateNextJoinCandidateCache();
            return true;
        }

        private void prune() {
            boolean changed = false;
            Iterator<RibbitEntity> iterator = this.members.iterator();
            while (iterator.hasNext()) {
                RibbitEntity member = iterator.next();
                if (!isValidMember(member)) {
                    iterator.remove();
                    this.formedMembers.remove(member);
                    this.joiningProgress.remove(member);
                    changed = true;
                }
            }

            changed |= this.formedMembers.removeIf(member -> !this.members.contains(member));
            Iterator<RibbitEntity> candidateIterator = this.gatheringCandidates.iterator();
            while (candidateIterator.hasNext()) {
                RibbitEntity candidate = candidateIterator.next();
                if (!isValidMember(candidate)) {
                    candidateIterator.remove();
                    this.joiningProgress.remove(candidate);
                    changed = true;
                }
            }

            this.joiningProgress.keySet().removeIf(member -> !this.members.contains(member)
                    && !this.gatheringCandidates.contains(member));
            changed |= this.releasedNoProgressMembers.removeIf(member -> !isValidMember(member));
            if (changed) {
                this.invalidateNextJoinCandidateCache();
            }
        }

        private static boolean isValidMember(@Nullable RibbitEntity member) {
            return member != null
                    && !member.isRemoved()
                    && !member.isDeadOrDying()
                    && member.isDayActivityTime()
                    && !member.isAutonomousAiPaused()
                    && !member.isInRain();
        }

        private static class JoiningProgress {
            private RibbitEntity target;
            private double bestDistanceSqr;
            private int ticksWithoutProgress;

            private JoiningProgress(RibbitEntity target, double distanceSqr) {
                this.target = target;
                this.bestDistanceSqr = distanceSqr;
            }

            private void reset(RibbitEntity target, double distanceSqr) {
                this.target = target;
                this.bestDistanceSqr = distanceSqr;
                this.ticksWithoutProgress = 0;
            }
        }

        private static class WaypointProgress {
            @Nullable
            private BlockPos target;
            private double bestDistanceSqr = Double.MAX_VALUE;
            private int ticksWithoutProgress;
            private int lastCheckedTick;

            private void reset(BlockPos target, double distanceSqr, int tickCount) {
                this.target = target.immutable();
                this.bestDistanceSqr = distanceSqr;
                this.ticksWithoutProgress = 0;
                this.lastCheckedTick = tickCount;
            }

            private boolean isStalled(BlockPos target, double distanceSqr, int tickCount) {
                if (!target.equals(this.target)) {
                    this.reset(target, distanceSqr, tickCount);
                    return false;
                }

                int elapsedTicks = tickCount - this.lastCheckedTick;
                this.lastCheckedTick = tickCount;
                if (distanceSqr < this.bestDistanceSqr - LEADER_WAYPOINT_PROGRESS_DISTANCE_SQR) {
                    this.bestDistanceSqr = distanceSqr;
                    this.ticksWithoutProgress = 0;
                    return false;
                }

                if (elapsedTicks > 0) {
                    this.ticksWithoutProgress += elapsedTicks;
                }
                return this.ticksWithoutProgress >= LEADER_WAYPOINT_STUCK_TICKS;
            }
        }
    }
}
