package me.jellysquid.mods.sodium.client.render.chunk.cull.graph;

import lombok.Getter;
import lombok.Setter;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import me.jellysquid.mods.sodium.common.util.DirectionUtil;
import net.minecraft.client.render.chunk.ChunkOcclusionData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class ChunkGraphNode {
    private static final long DEFAULT_VISIBILITY_DATA = calculateVisibilityData(ChunkRenderData.EMPTY.getOcclusionData());
    private static final float FRUSTUM_EPSILON = 1.0f /* block model margin */ + 0.125f /* epsilon */;

    private final ChunkGraphNode[] nodes = new ChunkGraphNode[DirectionUtil.ALL_DIRECTIONS.length];

    @Getter
    private final int chunkX;
    @Getter
    private final int chunkY;
    @Getter
    private final int chunkZ;

    @Getter
    private final int id;

    @Getter
    @Setter
    private int lastVisibleFrame = -1;

    private long visibilityData;
    private short cullingState;

    public ChunkGraphNode(int chunkX, int chunkY, int chunkZ, int id) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.chunkZ = chunkZ;
        this.id = id;

        this.visibilityData = DEFAULT_VISIBILITY_DATA;
    }

    public ChunkGraphNode getConnectedNode(Direction dir) {
        return this.nodes[dir.ordinal()];
    }

    public void setAdjacentNode(Direction dir, ChunkGraphNode node) {
        this.nodes[dir.ordinal()] = node;
    }

    public void setOcclusionData(ChunkOcclusionData occlusionData) {
        this.visibilityData = calculateVisibilityData(occlusionData);
    }

    private static long calculateVisibilityData(ChunkOcclusionData occlusionData) {
        long visibilityData = 0;

        for (Direction from : DirectionUtil.ALL_DIRECTIONS) {
            for (Direction to : DirectionUtil.ALL_DIRECTIONS) {
                if (occlusionData == null || occlusionData.isVisibleThrough(from, to)) {
                    visibilityData |= (1L << ((from.ordinal() << 3) + to.ordinal()));
                }
            }
        }

        return visibilityData;
    }

    //The way this works now is that the culling state contains 2 inner states
    // visited directions mask, and visitable direction mask
    //On graph start, the root node(s) have the visit and visitable masks set to all visible
    // when a chunk section is popped off the queue, the visited direction mask is anded with the
    // visitable direction mask to return a bitfield containing what directions the graph can flow too
    //When a chunk is visited in the graph the inbound direction is masked off from the visited direction mask
    // and the visitable direction mask is updated (ored) with the visibilityData of the inbound direction
    //When a chunk hasn't been visited before, it uses the parents data as the initial visited direction mask

    public short computeQueuePop() {
        short retVal = (short) (cullingState & (((cullingState >> 8) & 0xFF) | 0xFF00));
        cullingState = 0;
        return retVal;
    }

    public void updateCullingState(Direction flow, short parent) {
        int inbound = flow.ordinal();
        this.cullingState |= (short) ((visibilityData >> (inbound << 3)) & 0xFF);
        this.cullingState &= (short) ~(1 << (inbound + 8));
        // NOTE: this isn't strictly needed, due to the properties provided from the bfs search (never backtracking),
        // but just in-case/ better readability / understandability
        this.cullingState &= (short) (parent | 0x00FF);
    }

    public void setCullingState(short parent) {
        this.cullingState = (short) (parent & 0xFF00);
    }

    public void resetCullingState() {
        this.cullingState = -1;
    }

    public boolean isCulledByFrustum(FrustumExtended frustum) {
        float x = this.getOriginX();
        float y = this.getOriginY();
        float z = this.getOriginZ();

        return !frustum.fastAabbTest(x - FRUSTUM_EPSILON, y - FRUSTUM_EPSILON, z - FRUSTUM_EPSILON,
                x + 16.0f + FRUSTUM_EPSILON, y + 16.0f + FRUSTUM_EPSILON, z + 16.0f + FRUSTUM_EPSILON);
    }

    /**
     * @return The x-coordinate of the origin position of this chunk render
     */
    public int getOriginX() {
        return this.chunkX << 4;
    }

    /**
     * @return The y-coordinate of the origin position of this chunk render
     */
    public int getOriginY() {
        return this.chunkY << 4;
    }

    /**
     * @return The z-coordinate of the origin position of this chunk render
     */
    public int getOriginZ() {
        return this.chunkZ << 4;
    }

    /**
     * @return The squared distance from the center of this chunk in the world to the center of the block position
     * given by {@param pos}
     */
    public double getSquaredDistance(BlockPos pos) {
        return this.getSquaredDistance(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    /**
     * @return The x-coordinate of the center position of this chunk render
     */
    private double getCenterX() {
        return this.getOriginX() + 8.0D;
    }

    /**
     * @return The y-coordinate of the center position of this chunk render
     */
    private double getCenterY() {
        return this.getOriginY() + 8.0D;
    }

    /**
     * @return The z-coordinate of the center position of this chunk render
     */
    private double getCenterZ() {
        return this.getOriginZ() + 8.0D;
    }

    /**
     * @return The squared distance from the center of this chunk in the world to the given position
     */
    public double getSquaredDistance(double x, double y, double z) {
        double xDist = x - this.getCenterX();
        double yDist = y - this.getCenterY();
        double zDist = z - this.getCenterZ();

        return (xDist * xDist) + (yDist * yDist) + (zDist * zDist);
    }
}
