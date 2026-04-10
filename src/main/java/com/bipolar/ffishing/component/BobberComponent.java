package com.bipolar.ffishing.component;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.bipolar.ffishing.FallenFishingPlugin;
import com.bipolar.ffishing.util.FishCatchInfo;
import com.bipolar.ffishing.util.FishHelper;

public class BobberComponent implements Component<EntityStore> {
	private static final int DEFAULT_CATCH_WINDOW = 100;

	private int bobberAge;
	private int timeUntilCatch;
	private boolean canCatch;
	private int catchTimer;
	private Vector3i targetWater;
	private FishCatchInfo pendingCatch;

	public BobberComponent() {
		this.bobberAge = 0;
		this.canCatch = false;
		this.timeUntilCatch = -1;
		this.catchTimer = 0;
	}

	public static ComponentType<EntityStore, BobberComponent> getComponentType() {
		return FallenFishingPlugin.get().getBobberComponent();
	}

	public int getBobberAge() { return bobberAge; }
	public void setBobberAge(int bobberAge) { this.bobberAge = bobberAge; }

	public void setCanCatch(boolean canCatch) {
		this.canCatch = canCatch;
		if (canCatch) {
			this.catchTimer = DEFAULT_CATCH_WINDOW;
		} else {
			this.catchTimer = 0;
			this.pendingCatch = null;
		}
	}

	public void setCanCatch(boolean canCatch, int catchWindowTicks) {
		this.canCatch = canCatch;
		if (canCatch) {
			this.catchTimer = Math.max(12, catchWindowTicks);
		} else {
			this.catchTimer = 0;
			this.pendingCatch = null;
		}
	}

	public void setCatchTimer(int catchTimer) { this.catchTimer = catchTimer; }
	public int getTimeUntilCatch() { return timeUntilCatch; }
	public void setTimeUntilCatch(int timeUntilCatch) { this.timeUntilCatch = timeUntilCatch; }

	public void setRandomTimeUntilCatch() {
		this.timeUntilCatch = FishHelper.getTimeUntilCatch();
		this.pendingCatch = null;
	}

	public int getCatchTimer() { return catchTimer; }
	public boolean canCatchFish() { return this.canCatch && this.catchTimer > 0; }

	public Vector3i getTargetWater() { return targetWater; }
	public void setTargetWater(Vector3i targetWater) { this.targetWater = targetWater; }

	public FishCatchInfo getPendingCatch() { return pendingCatch; }
	public void setPendingCatch(FishCatchInfo pendingCatch) { this.pendingCatch = pendingCatch; }

	@Override
	public Component<EntityStore> clone() {
		BobberComponent component = new BobberComponent();
		component.bobberAge = this.bobberAge;
		component.canCatch = this.canCatch;
		component.timeUntilCatch = this.timeUntilCatch;
		component.catchTimer = this.catchTimer;
		component.targetWater = this.targetWater == null ? null : new Vector3i(this.targetWater.x, this.targetWater.y, this.targetWater.z);
		component.pendingCatch = this.pendingCatch;
		return component;
	}
}
