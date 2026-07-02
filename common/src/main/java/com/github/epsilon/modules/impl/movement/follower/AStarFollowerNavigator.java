package com.github.epsilon.modules.impl.movement.follower;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class AStarFollowerNavigator implements FollowerNavigator {

    private static final int[][] NEIGHBORS = {
            {1, 0, 0},
            {-1, 0, 0},
            {0, 0, 1},
            {0, 0, -1},
            {0, 1, 0},
            {0, -1, 0}
    };

    @Override
    public FollowerPath getPath(LocalPlayer player, LivingEntity target, Vec3 targetPos, FollowerConfig config) {
        Vec3 limitedTarget = limitTarget(player.position(), targetPos, config.searchRadius());

        if (isSegmentClear(player, player.position(), limitedTarget)) {
            return new FollowerPath(targetPos, List.of(player.position(), targetPos));
        }

        BlockPos start = BlockPos.containing(player.position());
        BlockPos goal = BlockPos.containing(limitedTarget);
        List<BlockPos> path = findPath(player, start, goal, config);

        if (path.size() > 1) {
            return createPath(player.position(), Vec3.atBottomCenterOf(path.get(1)), path);
        }
        if (path.size() == 1) {
            Vec3 point = Vec3.atBottomCenterOf(path.getFirst());
            return new FollowerPath(point, List.of(player.position(), point));
        }
        return new FollowerPath(targetPos, List.of(player.position(), targetPos));
    }

    private List<BlockPos> findPath(LocalPlayer player, BlockPos start, BlockPos goal, FollowerConfig config) {
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::fScore));
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Map<BlockPos, Double> gScore = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();

        Node startNode = new Node(start, 0.0, heuristic(start, goal));
        Node bestNode = startNode;
        open.add(startNode);
        gScore.put(start, 0.0);

        int visited = 0;
        while (!open.isEmpty() && visited < config.maxNodes()) {
            Node current = open.poll();
            if (!closed.add(current.pos())) continue;

            visited++;
            if (current.hScore() < bestNode.hScore()) {
                bestNode = current;
            }

            if (current.pos().equals(goal) || current.pos().distSqr(goal) <= config.stopDistance() * config.stopDistance()) {
                return reconstructPath(cameFrom, current.pos());
            }

            for (int[] offset : NEIGHBORS) {
                BlockPos next = current.pos().offset(offset[0], offset[1], offset[2]);
                if (closed.contains(next)) continue;
                if (start.distSqr(next) > config.searchRadius() * config.searchRadius()) continue;
                if (!canOccupy(player, next)) continue;

                double tentativeG = current.gScore() + movementCost(offset);
                double previousG = gScore.getOrDefault(next, Double.MAX_VALUE);
                if (tentativeG >= previousG) continue;

                cameFrom.put(next, current.pos());
                gScore.put(next, tentativeG);
                double h = heuristic(next, goal);
                open.add(new Node(next, tentativeG, h));
            }
        }

        if (!bestNode.pos().equals(start)) {
            return reconstructPath(cameFrom, bestNode.pos());
        }
        return List.of();
    }

    private boolean canOccupy(LocalPlayer player, BlockPos pos) {
        if (!player.level().isInWorldBounds(pos) || !player.level().hasChunkAt(pos)) {
            return false;
        }

        Vec3 feet = Vec3.atBottomCenterOf(pos);
        double halfWidth = player.getBbWidth() * 0.5 + 0.05;
        AABB box = new AABB(
                feet.x - halfWidth,
                feet.y,
                feet.z - halfWidth,
                feet.x + halfWidth,
                feet.y + player.getBbHeight(),
                feet.z + halfWidth
        );
        return player.level().noBlockCollision(player, box);
    }

    private boolean isSegmentClear(LocalPlayer player, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        double distance = delta.length();
        if (distance < 0.001) return true;

        int steps = Math.max(1, (int) Math.ceil(distance / 0.75));
        for (int i = 1; i <= steps; i++) {
            Vec3 point = from.add(delta.scale((double) i / steps));
            if (!canOccupy(player, BlockPos.containing(point))) {
                return false;
            }
        }
        return true;
    }

    private Vec3 limitTarget(Vec3 from, Vec3 target, int searchRadius) {
        Vec3 delta = target.subtract(from);
        double distance = delta.length();
        if (distance <= searchRadius || distance < 0.001) {
            return target;
        }
        return from.add(delta.normalize().scale(searchRadius));
    }

    private double heuristic(BlockPos pos, BlockPos goal) {
        return Math.sqrt(pos.distSqr(goal));
    }

    private double movementCost(int[] offset) {
        return Math.sqrt(offset[0] * offset[0] + offset[1] * offset[1] + offset[2] * offset[2]);
    }

    private List<BlockPos> reconstructPath(Map<BlockPos, BlockPos> cameFrom, BlockPos end) {
        ArrayList<BlockPos> path = new ArrayList<>();
        BlockPos current = end;
        path.add(current);

        while (cameFrom.containsKey(current)) {
            current = cameFrom.get(current);
            path.add(current);
        }

        Collections.reverse(path);
        return path;
    }

    private FollowerPath createPath(Vec3 playerPos, Vec3 nextPoint, List<BlockPos> nodes) {
        ArrayList<Vec3> points = new ArrayList<>();
        points.add(playerPos);

        for (int i = 1; i < nodes.size(); i++) {
            points.add(Vec3.atBottomCenterOf(nodes.get(i)));
        }

        return new FollowerPath(nextPoint, List.copyOf(points));
    }

    private record Node(BlockPos pos, double gScore, double hScore) {
        private double fScore() {
            return gScore + hScore;
        }
    }

}
