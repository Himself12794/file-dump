package com.himself12794.notenoughtime.handlers.common;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.himself12794.notenoughtime.NotEnoughTime;
import com.himself12794.notenoughtime.world.TimeFlowData;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import net.minecraft.network.play.server.S03PacketTimeUpdate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import scala.tools.nsc.transform.patmat.Logic.PropositionalLogic.Not;

public class TimeFlowHandler {
	
	private int dayLength = 24000;
	private int nightTime = 12000;
	private boolean getManagerFailed = false;
	private ServerConfigurationManager configManager = null;
	
	public TimeFlowHandler() {
	}
	
	@SubscribeEvent
	public void onWorldTick(TickEvent.WorldTickEvent event) {
		
		if (configManager == null && !getManagerFailed) {
			configManager = getServerConfigManager();
		}
		
		if (event.phase == Phase.START) {
			handleServerSleeping(event.world);
			updateTime(event.world);
		} 
	}

	/**
	 * Run every tick to manually update the time if this mod is enabled, since normal daylight cycle 
	 * has been halted.
	 * 
	 * @param world
	 */
	public void updateTime(World world) {
		long worldTime = getTime(world);
		long adjustedTime = worldTime % dayLength;
		boolean isDay = adjustedTime < nightTime;
		TimeFlowData data = TimeFlowData.getForWorld(world);
		
		if (data.isEnabled) {
		
			world.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
			float multiplier;
			if (isDay) {
				multiplier = data.dayLengthMultiplier;
			} else {
				multiplier = data.nightLengthMultiplier;
			}
			int fullTicks = (int)multiplier;
			float partialTicks = data.partialTickCounter;
			if (Math.abs(partialTicks) >= 1.0F) {
				fullTicks += (int)partialTicks;
				data.partialTickCounter = getPartialValue(partialTicks);
			}
			float partialMult = getPartialValue(multiplier);
			data.partialTickCounter += partialMult;
			worldTime += fullTicks;
			
			// Prevent negative time
			if (worldTime < 0) {
				worldTime %= dayLength;
				worldTime += dayLength;
			}
			setTime(world, worldTime);
			
			if (configManager != null && NotEnoughTime.config().updateTimePerTick()) {
				configManager.sendPacketToAllPlayersInDimension(new S03PacketTimeUpdate(world.getTotalWorldTime(), world.getWorldTime(), data.isEnabled), world.provider.dimensionId);
			}
		} 
		
		data.markDirty();
		
	}
	
	public long getTime(World world) {
		return world.isRemote ? 
				world.getWorldTime() : 
					world.getWorldInfo().getWorldTime();
	}
	
	public void setTime(World world, long time) {
		
		if (world.isRemote) {
			world.setWorldTime(time);
		} else {
			world.getWorldInfo().setWorldTime(time);
		}
		
	}
	
	/**
	 * Replaces normal sleeping logic with doDaylightCycle on, so time will still advance
	 * if time modding is activated
	 * 
	 * @param world
	 */
	public void handleServerSleeping(World world) {
		WorldServer server = (WorldServer)world;
		
		if (server.areAllPlayersAsleep()) {
            if (TimeFlowData.getForWorld(server).isEnabled) {
                long i = server.getWorldInfo().getWorldTime() + 24000L;
                server.getWorldInfo().setWorldTime(i - i % 24000L);
            }
            
            try {
	            Method method = server.getClass().getMethod("wakeAllPLayers");
	            method.setAccessible(true);
	            method.invoke(server);
            } catch (Exception e) {
            	
            } 
        }
	}
	
	public ServerConfigurationManager getServerConfigManager() {
		
		try {
			Field field = MinecraftServer.class.getDeclaredField("serverConfigManager");
			field.setAccessible(true);
			return (ServerConfigurationManager)field.get(MinecraftServer.getServer());
		} catch (Exception e) {
			getManagerFailed = true;
		}
		return null;
	}
	
	/**
	 * Gets the partial value of a number, which is the value after the decimal.
	 * 
	 * @param value
	 * @return
	 */
	private static float getPartialValue(float value) {
		int head = value < 0 ? MathHelper.ceiling_float_int(value) : MathHelper.floor_float(value);
		return value - head;
	}
	
}