package org.valkyrienskies.mod.common.ships.ship_transform;

import net.minecraft.util.math.AxisAlignedBB;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.collision.Polygon;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import valkyrienwarfare.api.TransformType;

/**
 * Stores various coordinates and transforms for the ship.
 */
public class ShipTransformationManager {

    private final PhysicsObject parent;
    public Vector3dc[] normals;
    private ShipTransform currentTickTransform;
    private ShipTransform renderTransform;
    private ShipTransform prevTickTransform;
    // Used exclusively by the physics engine; should never even be used by the
    // client.
    private ShipTransform currentPhysicsTransform;
    private ShipTransform prevPhysicsTransform;

    public ShipTransformationManager(PhysicsObject parent, ShipTransform initialTransform) {
        this.parent = parent;
        this.currentTickTransform = initialTransform;
        this.renderTransform = initialTransform;
        this.prevTickTransform = initialTransform;
        this.currentPhysicsTransform = initialTransform;
        this.prevPhysicsTransform = initialTransform;
        // Create the normals.
        this.normals = createCollisionNormals(initialTransform);
    }

    private static Vector3dc[] createCollisionNormals(ShipTransform transform) {
        // We edit a local array instead of normals to avoid data races.
        final Vector3dc[] newNormals = new Vector3dc[15];
        // Used to generate Normals for the Axis Aligned World
        final Vector3dc[] alignedNorms = Polygon.generateAxisAlignedNorms();
        final Vector3dc[] rotatedNorms = generateRotationNormals(transform);
        for (int i = 0; i < 6; i++) {
            Vector3dc currentNorm;
            if (i < 3) {
                currentNorm = alignedNorms[i];
            } else {
                currentNorm = rotatedNorms[i - 3];
            }
            newNormals[i] = currentNorm;
        }
        int cont = 6;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                Vector3d norm = newNormals[i].cross(newNormals[j + 3], new Vector3d());
                norm.normalize();
                newNormals[cont] = norm;
                cont++;
            }
        }
        for (int i = 0; i < newNormals.length; i++) {
            if (newNormals[i].lengthSquared() < .01) {
                newNormals[i] = new Vector3d(0.0D, 1.0D, 0.0D);
            }
        }
        newNormals[0] = new Vector3d(1, 0, 0);
        newNormals[1] = new Vector3d(0, 1, 0);
        newNormals[2] = new Vector3d(0, 0, 1);

        return newNormals;
    }

    /*
    public void sendPositionToPlayers(int positionTickID) {
        WrapperPositionMessage posMessage = null;
        Matrix4dc gts = getCurrentPhysicsTransform().getGlobalToSubspace();
        Matrix4dc stg = getCurrentPhysicsTransform().getSubspaceToGlobal();
        // If it is the identity transform
        if ((gts.properties() & Matrix4dc.PROPERTY_IDENTITY) != 0 &&
            (stg.properties() & Matrix4dc.PROPERTY_IDENTITY) != 0) {
            posMessage = new WrapperPositionMessage(
                (PhysicsShipTransform) getCurrentPhysicsTransform(),
                parent.getWrapperEntity().getEntityId(), positionTickID);
        } else {
            posMessage = new WrapperPositionMessage(parent.getWrapperEntity(), positionTickID);
        }

        // Do a standard loop here to avoid a concurrentModificationException. A standard for each loop could cause a crash.
        for (int i = 0; i < parent.getWatchingPlayers().size(); i++) {
            EntityPlayerMP player = parent.getWatchingPlayers().get(i);
            if (player != null) {
                ValkyrienSkiesMod.physWrapperNetwork.sendTo(posMessage, player);
            }
        }
    }
     */

    private static Vector3dc[] generateRotationNormals(ShipTransform shipTransform) {
        Vector3d[] norms = Polygon.generateAxisAlignedNorms();
        for (int i = 0; i < 3; i++) {
            shipTransform.transformDirection(norms[i], TransformType.SUBSPACE_TO_GLOBAL);
        }
        return norms;
    }

    /**
     * Updates all the transformations, only updates the AABB if passed true.
     */
    @Deprecated
    public void updateAllTransforms(ShipTransform newTransform, boolean updatePhysicsTransform, boolean updateParentAABB) {
        prevTickTransform = currentTickTransform;
        currentTickTransform = newTransform;
        // The client should never be updating the AABB on its own.
        if (parent.getWorld().isRemote) {
            updateParentAABB = false;
        }

        if (prevTickTransform == null) {
            prevTickTransform = currentTickTransform;
        }
        if (updatePhysicsTransform) {
            // This should only be called once when the ship finally loads from nbt.
            parent.getPhysicsCalculations()
                    .generatePhysicsTransform();
            prevPhysicsTransform = currentPhysicsTransform;
        }
        if (updateParentAABB) {
            updateParentAABB();
        }
        this.normals = createCollisionNormals(currentTickTransform);
    }

    // TODO: Use Octrees to optimize this, or more preferably QuickHull3D.
    private void updateParentAABB() {
        AxisAlignedBB subspaceBB = parent.getBlockPositions().makeAABB();
        if (subspaceBB == null) {
            // The aabbMaker didn't know what the aabb was, just don't update the aabb for now.
            return;
        }
        // Expand subspaceBB by 1 to fit the block grid.
        subspaceBB = subspaceBB.expand(1, 1, 1);
        // Now transform the subspaceBB to world coordinates
        Polygon largerPoly = new Polygon(subspaceBB, getCurrentTickTransform(),
            TransformType.SUBSPACE_TO_GLOBAL);
        // Set the ship AABB to that of the polygon.
        AxisAlignedBB worldBB = largerPoly.getEnclosedAABB();
        parent.setShipBoundingBox(worldBB);
    }

    /**
     * @return the current transformation being used this tick.
     */
    public ShipTransform getCurrentTickTransform() {
        return currentTickTransform;
    }

    /**
     * @param currentTransform the currentTransform to set
     */
    @Deprecated
    private void setCurrentTickTransform(ShipTransform currentTransform) {
        this.currentTickTransform = currentTransform;
    }

    /**
     * @return the renderTransform
     */
    public ShipTransform getRenderTransform() {
        if (!this.parent.getWorld().isRemote || renderTransform == null) {
            return currentTickTransform;
        }
        return renderTransform;
    }

    /**
     * @return the prevTransform
     */
    public ShipTransform getPrevTickTransform() {
        return prevTickTransform;
    }

    /**
     * Returns the transformation data used for physics processing.
     *
     * @return the physics transform
     */
    public ShipTransform getCurrentPhysicsTransform() {
        return currentPhysicsTransform;
    }

    /**
     * Sets the physics transform to the given input.
     */
    public void setCurrentPhysicsTransform(ShipTransform currentPhysicsTransform) {
        this.currentPhysicsTransform = currentPhysicsTransform;
    }

    public ShipTransform getPrevPhysicsTransform() {
        return prevPhysicsTransform;
    }

    public void updatePreviousPhysicsTransform() {
        this.prevPhysicsTransform = currentPhysicsTransform;
    }

    public void updateRenderTransform(double partialTick) {
        if (partialTick == 0) {
            renderTransform = prevTickTransform;
            return;
        } else if (partialTick == 1) {
            renderTransform = currentTickTransform;
            return;
        }
        ShipTransform prev = prevTickTransform;
        ShipTransform cur = currentTickTransform;
        Vector3dc shipCenter = parent.getCenterCoord();

        Vector3d prevPos = new Vector3d(shipCenter);
        Vector3d curPos = new Vector3d(shipCenter);
        prev.transformPosition(prevPos, TransformType.SUBSPACE_TO_GLOBAL);
        cur.transformPosition(curPos, TransformType.SUBSPACE_TO_GLOBAL);
        Vector3d deltaPos = curPos.sub(prevPos, new Vector3d());
        deltaPos.mul(partialTick);

        Vector3d partialPos = new Vector3d(prevPos);
        partialPos.add(deltaPos); // Now partialPos is complete

        Quaterniondc prevRot = prev.rotationQuaternion(TransformType.SUBSPACE_TO_GLOBAL);
        Quaterniondc curRot = cur.rotationQuaternion(TransformType.SUBSPACE_TO_GLOBAL);
        Quaterniondc partialRot = prevRot.slerp(curRot, partialTick, new Quaterniond()).normalize();

        // Put it all together to get the render transform.
        renderTransform = new ShipTransform(partialPos.x, partialPos.y,
            partialPos.z, partialRot, parent.getCenterCoord());
    }

}
