package valkyrienwarfare.mod.network;

import net.minecraft.entity.Entity;
import net.minecraft.util.IThreadListener;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import valkyrienwarfare.mod.coordinates.ISubspacedEntity;
import valkyrienwarfare.mod.coordinates.ISubspacedEntityRecord;
import valkyrienwarfare.mod.physmanagement.interaction.IDraggable;
import valkyrienwarfare.physics.management.PhysicsWrapperEntity;

public class SubspacedEntityRecordHandler implements IMessageHandler<SubspacedEntityRecordMessage, IMessage> {

	@Override
	public IMessage onMessage(final SubspacedEntityRecordMessage message, final MessageContext ctx) {
		IThreadListener threadScheduler = null;
		World world = null;
		if (ctx.side.isClient()) {
			// We are receiving this on the client
			threadScheduler = getClientThreadListener();
			world = getClientWorld();
		} else {
			// Otherwise we are receiving this on the server
			threadScheduler = ctx.getServerHandler().serverController;
			world = ctx.getServerHandler().player.world;
		}
		final World worldFinal = world;
		threadScheduler.addScheduledTask(() -> {
			Entity physicsEntity = worldFinal.getEntityByID(message.physicsObjectWrapperID);
			Entity subspacedEntity = worldFinal.getEntityByID(message.entitySubspacedID);
			if (physicsEntity != null && subspacedEntity != null) {
				PhysicsWrapperEntity wrapperEntity = (PhysicsWrapperEntity) physicsEntity;
				ISubspacedEntityRecord record = message.createRecordForThisMessage(
						ISubspacedEntity.class.cast(subspacedEntity), wrapperEntity.getPhysicsObject().getSubspace());
				IDraggable draggable = IDraggable.class.cast(subspacedEntity);
				draggable.setForcedRelativeSubspace(wrapperEntity);
				wrapperEntity.getPhysicsObject().getSubspace().forceSubspaceRecord(record.getParentEntity(), record);
				// Now just synchronize the player to the data sent by the client.
			} else {
				System.err.println("An incorrect SubspacedEntityRecordMessage has been thrown out");
			}
		});
		return null;
	}

	@SideOnly(Side.CLIENT)
	private IThreadListener getClientThreadListener() {
		return net.minecraft.client.Minecraft.getMinecraft();
	}
	
	@SideOnly(Side.CLIENT)
	private World getClientWorld() {
		return net.minecraft.client.Minecraft.getMinecraft().world;
	}

}
