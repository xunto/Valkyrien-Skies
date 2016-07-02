package ValkyrienWarfareBase.Render;

import org.lwjgl.opengl.GL11;

import ValkyrienWarfareBase.Math.Vector;
import ValkyrienWarfareBase.PhysicsManagement.PhysicsObject;
import ValkyrienWarfareBase.PhysicsManagement.PhysicsWrapperEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.VertexBuffer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;

/**
 * Object owned by each physObject responsible for handling all rendering operations
 * @author thebest108
 *
 */
public class PhysObjectRenderManager {

	public boolean needsSolidUpdate=true,needsCutoutUpdate=true,needsCutoutMippedUpdate=true,needsTranslucentUpdate=true;
	public int glCallListSolid = -1;
	public int glCallListTranslucent = -1;
	public int glCallListCutout = -1;
	public int glCallListCutoutMipped = -1;
	public PhysicsObject parent;
	//This pos is used to prevent Z-Buffer Errors D:
	//It's actual value is completely irrelevant as long as it's close to the 
	//Ship's centerBlockPos
	public BlockPos offsetPos;
	
	public PhysObjectRenderManager(PhysicsObject toRender){
		parent = toRender;
	}
	
	public void updateOffsetPos(BlockPos newPos){
		offsetPos = newPos;
	}
	
	public void updateList(BlockRenderLayer layerToUpdate){
		if(offsetPos==null){
			return;
		}
		Tessellator tessellator = Tessellator.getInstance();
		VertexBuffer worldrenderer = tessellator.getBuffer();
		
		worldrenderer.setTranslation(-offsetPos.getX(), -offsetPos.getY(), -offsetPos.getZ());
		GL11.glPushMatrix();
		switch(layerToUpdate){
			case CUTOUT:
				GLAllocation.deleteDisplayLists(glCallListCutout);
				glCallListCutout = GLAllocation.generateDisplayLists(1);
				GL11.glNewList(glCallListCutout, GL11.GL_COMPILE);
				break;
			case CUTOUT_MIPPED:
				GLAllocation.deleteDisplayLists(glCallListCutoutMipped);
				glCallListCutoutMipped = GLAllocation.generateDisplayLists(1);
				GL11.glNewList(glCallListCutoutMipped, GL11.GL_COMPILE);
				break;
			case SOLID:
				GLAllocation.deleteDisplayLists(glCallListSolid);
				glCallListSolid = GLAllocation.generateDisplayLists(1);
				GL11.glNewList(glCallListSolid, GL11.GL_COMPILE);
				break;
			case TRANSLUCENT:
				GLAllocation.deleteDisplayLists(glCallListTranslucent);
				glCallListTranslucent = GLAllocation.generateDisplayLists(1);
				GL11.glNewList(glCallListTranslucent, GL11.GL_COMPILE);
				break;
			default:
				break;
		}
			
		GlStateManager.pushMatrix();
		worldrenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
		IBlockState iblockstate;
//		if (Minecraft.isAmbientOcclusionEnabled()) {
//			GlStateManager.shadeModel(GL11.GL_SMOOTH);
//		} else {
//			GlStateManager.shadeModel(GL11.GL_FLAT);
//		}
		for(BlockPos pos:parent.blockPositions){
			iblockstate=parent.worldObj.getBlockState(pos);
			if(iblockstate.getBlock().canRenderInLayer(iblockstate, layerToUpdate)){
				Minecraft.getMinecraft().getBlockRendererDispatcher().renderBlock(iblockstate, pos, parent.worldObj, worldrenderer);
			}
		}
		tessellator.draw();
		GlStateManager.popMatrix();
		GL11.glEndList();
		GL11.glPopMatrix();
		worldrenderer.setTranslation(0,0,0);

		switch(layerToUpdate){
			case CUTOUT:
				needsCutoutUpdate = false;
				break;
			case CUTOUT_MIPPED:
				needsCutoutMippedUpdate = false;
				break;
			case SOLID:
				needsSolidUpdate = false;
				break;
			case TRANSLUCENT:
				needsTranslucentUpdate = false;
				break;
			default:
				break;
		}
	}
	
	public void renderBlockLayer(BlockRenderLayer layerToRender,double partialTicks,int pass){
		GL11.glPushMatrix();
		Minecraft.getMinecraft().entityRenderer.enableLightmap();
		int i = parent.wrapper.getBrightnessForRender((float) partialTicks);

        int j = i % 65536;
        int k = i / 65536;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (float)j, (float)k);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		
		setupTranslation(partialTicks);
		switch(layerToRender){
			case CUTOUT:
				GL11.glCallList(glCallListCutout);
				break;
			case CUTOUT_MIPPED:
				GL11.glCallList(glCallListCutoutMipped);
				break;
			case SOLID:
				GL11.glCallList(glCallListSolid);
				break;
			case TRANSLUCENT:
				GL11.glCallList(glCallListTranslucent);
				break;
			default:
				break;
		}
		Minecraft.getMinecraft().entityRenderer.disableLightmap();
		GL11.glPopMatrix();
	}
	
	public void setupTranslation(double partialTicks){
		PhysicsWrapperEntity entity = parent.wrapper;
		
//		entity.wrapping.yaw++;
		
		Vector centerOfRotation = entity.wrapping.centerCoord;
		double moddedX = entity.lastTickPosX+(entity.posX-entity.lastTickPosX)*partialTicks;
		double moddedY = entity.lastTickPosY+(entity.posY-entity.lastTickPosY)*partialTicks;
		double moddedZ = entity.lastTickPosZ+(entity.posZ-entity.lastTickPosZ)*partialTicks;
		double p0 = Minecraft.getMinecraft().thePlayer.lastTickPosX + (Minecraft.getMinecraft().thePlayer.posX - Minecraft.getMinecraft().thePlayer.lastTickPosX) * (double)partialTicks;
		double p1 = Minecraft.getMinecraft().thePlayer.lastTickPosY + (Minecraft.getMinecraft().thePlayer.posY - Minecraft.getMinecraft().thePlayer.lastTickPosY) * (double)partialTicks;
		double p2 = Minecraft.getMinecraft().thePlayer.lastTickPosZ + (Minecraft.getMinecraft().thePlayer.posZ - Minecraft.getMinecraft().thePlayer.lastTickPosZ) * (double)partialTicks;
		
		double moddedPitch = entity.wrapping.pitch;
		double moddedYaw = entity.wrapping.yaw;
		double moddedRoll = entity.wrapping.roll;
		
		if(entity.wrapping.renderer.offsetPos!=null){
			double offsetX = entity.wrapping.renderer.offsetPos.getX()-centerOfRotation.X;
			double offsetY = entity.wrapping.renderer.offsetPos.getY()-centerOfRotation.Y;
			double offsetZ = entity.wrapping.renderer.offsetPos.getZ()-centerOfRotation.Z;
			//This happens first
			GL11.glTranslated(offsetX,offsetY,offsetZ);
			GlStateManager.translate(-p0+moddedX, -p1+moddedY, -p2+moddedZ);
			//This happens BETWEEN
			GL11.glRotated(moddedPitch, 1D, 0, 0);
			GL11.glRotated(moddedYaw, 0, 1D, 0);
			GL11.glRotated(moddedRoll, 0, 0, 1D);
			
			//This is supposed to happen last
			
		}
	}
	
	public void markForUpdate(){
		needsCutoutUpdate = true;
		needsCutoutMippedUpdate = true;
		needsSolidUpdate = true;
		needsTranslucentUpdate = true;
	}
	
}