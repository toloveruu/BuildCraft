package buildcraft.silicon;

import buildcraft.core.recipes.AssemblyRecipeManager;
import buildcraft.api.power.ILaserTarget;
import buildcraft.api.gates.IAction;
import buildcraft.core.DefaultProps;
import buildcraft.core.IMachine;
import buildcraft.core.inventory.InvUtils;
import buildcraft.core.network.PacketIds;
import buildcraft.core.network.PacketNBT;
import buildcraft.core.proxy.CoreProxy;
import buildcraft.core.utils.Utils;
import buildcraft.core.recipes.AssemblyRecipeManager.AssemblyRecipe;
import cpw.mods.fml.common.FMLCommonHandler;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.ForgeDirection;

public class TileAssemblyTable extends TileEntity implements IMachine, IInventory, ILaserTarget {

	ItemStack[] items = new ItemStack[12];
	Set<AssemblyRecipe> plannedOutput = new LinkedHashSet<AssemblyRecipe>();
	public AssemblyRecipe currentRecipe;
	private double currentRequiredEnergy = 0;
	private float energyStored = 0;
	private double[] recentEnergy = new double[20];
	private int tick = 0;
	private int recentEnergyAverage;

	public static class SelectionMessage {

		public boolean select;
		public ItemStack stack;

		public NBTTagCompound getNBT() {
			NBTTagCompound nbt = new NBTTagCompound();
			nbt.setBoolean("s", select);
			NBTTagCompound itemNBT = new NBTTagCompound();
			stack.writeToNBT(itemNBT);
			nbt.setCompoundTag("i", itemNBT);
			return nbt;
		}

		public void fromNBT(NBTTagCompound nbt) {
			select = nbt.getBoolean("s");
			NBTTagCompound itemNBT = nbt.getCompoundTag("i");
			stack = ItemStack.loadItemStackFromNBT(itemNBT);
		}
	}

	public List<AssemblyRecipe> getPotentialOutputs() {
		List<AssemblyRecipe> result = new LinkedList<AssemblyRecipe>();

		for (AssemblyRecipe recipe : AssemblyRecipeManager.INSTANCE.getRecipes()) {
			if (recipe.canBeDone(this))
				result.add(recipe);
		}

		return result;
	}

	@Override
	public boolean canUpdate() {
		return !FMLCommonHandler.instance().getEffectiveSide().isClient();
	}

	@Override
	public void updateEntity() { // WARNING: run only server-side, see canUpdate()
		tick++;
		tick = tick % recentEnergy.length;
		recentEnergy[tick] = 0.0f;

		if (currentRecipe == null)
			return;

		if (!currentRecipe.canBeDone(this)) {
			setNextCurrentRecipe();

			if (currentRecipe == null)
				return;
		}

		if (energyStored >= currentRecipe.getEnergyCost()) {
			energyStored = 0;

			if (currentRecipe.canBeDone(this)) {

				currentRecipe.useItems(this);

				ItemStack remaining = currentRecipe.output.copy();
				remaining.stackSize -= Utils.addToRandomInventoryAround(worldObj, xCoord, yCoord, zCoord, remaining);

				if (remaining.stackSize > 0) {
					remaining.stackSize -= Utils.addToRandomPipeAround(worldObj, xCoord, yCoord, zCoord, ForgeDirection.UNKNOWN, remaining);
				}

				if (remaining.stackSize > 0) {
					EntityItem entityitem = new EntityItem(worldObj, xCoord + 0.5, yCoord + 0.7, zCoord + 0.5, currentRecipe.output.copy());

					worldObj.spawnEntityInWorld(entityitem);
				}

				setNextCurrentRecipe();
			}
		}
	}

	public double getCompletionRatio(float ratio) {
		if (currentRecipe == null)
			return 0;
		else if (energyStored >= currentRequiredEnergy)
			return ratio;
		else
			return energyStored / currentRequiredEnergy * ratio;
	}

	/* IINVENTORY */
	@Override
	public int getSizeInventory() {
		return items.length;
	}

	@Override
	public ItemStack getStackInSlot(int i) {
		return items[i];
	}

	@Override
	public ItemStack decrStackSize(int i, int j) {
		ItemStack stack = items[i].splitStack(j);
		if (items[i].stackSize == 0) {
			items[i] = null;
		}
		return stack;
	}

	@Override
	public void setInventorySlotContents(int i, ItemStack itemstack) {
		items[i] = itemstack;

		if (currentRecipe == null) {
			setNextCurrentRecipe();
		}
	}

	@Override
	public ItemStack getStackInSlotOnClosing(int slot) {
		if (this.items[slot] == null)
			return null;

		ItemStack stackToTake = this.items[slot];
		this.items[slot] = null;
		return stackToTake;
	}

	@Override
	public String getInvName() {
		return "Assembly Table";
	}

	@Override
	public int getInventoryStackLimit() {
		return 64;
	}

	@Override
	public boolean isUseableByPlayer(EntityPlayer entityplayer) {
		return worldObj.getBlockTileEntity(xCoord, yCoord, zCoord) == this;
	}

	@Override
	public void openChest() {
	}

	@Override
	public void closeChest() {
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		super.readFromNBT(nbt);

		InvUtils.readStacksFromNBT(nbt, "items", items);

		energyStored = nbt.getFloat("energyStored");

		NBTTagList list = nbt.getTagList("planned");

		for (int i = 0; i < list.tagCount(); ++i) {
			NBTTagCompound cpt = (NBTTagCompound) list.tagAt(i);

			ItemStack stack = ItemStack.loadItemStackFromNBT(cpt);

			for (AssemblyRecipe r : AssemblyRecipeManager.INSTANCE.getRecipes()) {
				if (r.output.itemID == stack.itemID && r.output.getItemDamage() == stack.getItemDamage()) {
					plannedOutput.add(r);
					break;
				}
			}
		}

		if (nbt.hasKey("recipe")) {
			ItemStack stack = ItemStack.loadItemStackFromNBT(nbt.getCompoundTag("recipe"));

			for (AssemblyRecipe r : plannedOutput) {
				if (r.output.itemID == stack.itemID && r.output.getItemDamage() == stack.getItemDamage()) {
					setCurrentRecipe(r);
					break;
				}
			}
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		super.writeToNBT(nbt);

		InvUtils.writeStacksToNBT(nbt, "items", items);

		nbt.setFloat("energyStored", energyStored);

		NBTTagList list = new NBTTagList();

		for (AssemblyRecipe recipe : plannedOutput) {
			NBTTagCompound cpt = new NBTTagCompound();
			recipe.output.writeToNBT(cpt);
			list.appendTag(cpt);
		}

		nbt.setTag("planned", list);

		if (currentRecipe != null) {
			NBTTagCompound recipe = new NBTTagCompound();
			currentRecipe.output.writeToNBT(recipe);
			nbt.setTag("recipe", recipe);
		}
	}

	public boolean isPlanned(AssemblyRecipe recipe) {
		if (recipe == null)
			return false;

		return plannedOutput.contains(recipe);
	}

	public boolean isAssembling(AssemblyRecipe recipe) {
		return recipe != null && recipe == currentRecipe;
	}

	private void setCurrentRecipe(AssemblyRecipe recipe) {
		this.currentRecipe = recipe;
		if (recipe != null) {
			this.currentRequiredEnergy = recipe.getEnergyCost();
		} else {
			this.currentRequiredEnergy = 0;
		}
	}

	public void planOutput(AssemblyRecipe recipe) {
		if (recipe != null && !isPlanned(recipe)) {
			plannedOutput.add(recipe);

			if (!isAssembling(currentRecipe) || !isPlanned(currentRecipe)) {
				setCurrentRecipe(recipe);
			}
		}
	}

	public void cancelPlanOutput(AssemblyRecipe recipe) {
		if (isAssembling(recipe)) {
			setCurrentRecipe(null);
		}

		plannedOutput.remove(recipe);

		if (!plannedOutput.isEmpty()) {
			setCurrentRecipe(plannedOutput.iterator().next());
		}
	}

	public void setNextCurrentRecipe() {
		boolean takeNext = false;

		for (AssemblyRecipe recipe : plannedOutput) {
			if (recipe == currentRecipe) {
				takeNext = true;
			} else if (takeNext && recipe.canBeDone(this)) {
				setCurrentRecipe(recipe);
				return;
			}
		}

		for (AssemblyRecipe recipe : plannedOutput) {
			if (recipe.canBeDone(this)) {
				setCurrentRecipe(recipe);
				return;
			}
		}

		setCurrentRecipe(null);
	}

	public void handleSelectionMessage(SelectionMessage message) {
		for (AssemblyRecipe recipe : AssemblyRecipeManager.INSTANCE.getRecipes()) {
			if (recipe.output.isItemEqual(message.stack) && ItemStack.areItemStackTagsEqual(recipe.output, message.stack)) {
				if (message.select) {
					planOutput(recipe);
				} else {
					cancelPlanOutput(recipe);
				}

				break;
			}
		}
	}

	public void sendSelectionTo(EntityPlayer player) {
		for (AssemblyRecipe recipe : AssemblyRecipeManager.INSTANCE.getRecipes()) {
			SelectionMessage message = new SelectionMessage();

			message.stack = recipe.output;

			if (isPlanned(recipe)) {
				message.select = true;
			} else {
				message.select = false;
			}

			PacketNBT packet = new PacketNBT(PacketIds.SELECTION_ASSEMBLY_SEND, message.getNBT(), xCoord, yCoord, zCoord);
			packet.posX = xCoord;
			packet.posY = yCoord;
			packet.posZ = zCoord;
			// FIXME: This needs to be switched over to new synch system.
			CoreProxy.proxy.sendToPlayers(packet.getPacket(), worldObj, (int) player.posX, (int) player.posY, (int) player.posZ,
					DefaultProps.NETWORK_UPDATE_RANGE);
		}
	}

	/* SMP GUI */
	public void getGUINetworkData(int i, int j) {
		int currentStored = (int) (energyStored * 100.0);
		int requiredEnergy = (int) (currentRequiredEnergy * 100.0);
		switch (i) {
			case 0:
				requiredEnergy = (requiredEnergy & 0xFFFF0000) | (j & 0xFFFF);
				currentRequiredEnergy = (requiredEnergy / 100.0f);
				break;
			case 1:
				currentStored = (currentStored & 0xFFFF0000) | (j & 0xFFFF);
				energyStored = (currentStored / 100.0f);
				break;
			case 2:
				requiredEnergy = (requiredEnergy & 0xFFFF) | ((j & 0xFFFF) << 16);
				currentRequiredEnergy = (requiredEnergy / 100.0f);
				break;
			case 3:
				currentStored = (currentStored & 0xFFFF) | ((j & 0xFFFF) << 16);
				energyStored = (currentStored / 100.0f);
				break;
			case 4:
				recentEnergyAverage = recentEnergyAverage & 0xFFFF0000 | (j & 0xFFFF);
				break;
			case 5:
				recentEnergyAverage = (recentEnergyAverage & 0xFFFF) | ((j & 0xFFFF) << 16);
				break;
		}
	}

	public void sendGUINetworkData(Container container, ICrafting iCrafting) {
		int requiredEnergy = (int) (currentRequiredEnergy * 100.0);
		int currentStored = (int) (energyStored * 100.0);
		int lRecentEnergy = 0;
		for (int i = 0; i < recentEnergy.length; i++) {
			lRecentEnergy += (int) (recentEnergy[i] * 100.0 / (recentEnergy.length - 1));
		}
		iCrafting.sendProgressBarUpdate(container, 0, requiredEnergy & 0xFFFF);
		iCrafting.sendProgressBarUpdate(container, 1, currentStored & 0xFFFF);
		iCrafting.sendProgressBarUpdate(container, 2, (requiredEnergy >>> 16) & 0xFFFF);
		iCrafting.sendProgressBarUpdate(container, 3, (currentStored >>> 16) & 0xFFFF);
		iCrafting.sendProgressBarUpdate(container, 4, lRecentEnergy & 0xFFFF);
		iCrafting.sendProgressBarUpdate(container, 5, (lRecentEnergy >>> 16) & 0xFFFF);
	}

	@Override
	public boolean isActive() {
		return currentRecipe != null;
	}

	@Override
	public boolean manageFluids() {
		return false;
	}

	@Override
	public boolean manageSolids() {
		return false;
	}

	@Override
	public boolean allowAction(IAction action) {
		return false;
	}

	public int getRecentEnergyAverage() {
		return recentEnergyAverage;
	}

	public float getStoredEnergy() {
		return energyStored;
	}

	public double getRequiredEnergy() {
		return currentRequiredEnergy;
	}

	@Override
	public boolean requiresLaserEnergy() {
		return currentRecipe != null && energyStored < currentRequiredEnergy * 5F;
	}

	@Override
	public void receiveLaserEnergy(float energy) {
		energyStored += energy;
		recentEnergy[tick] += energy;
	}

	@Override
	public int getXCoord() {
		return xCoord;
	}

	@Override
	public int getYCoord() {
		return yCoord;
	}

	@Override
	public int getZCoord() {
		return zCoord;
	}

	@Override
	public boolean isInvNameLocalized() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack itemstack) {
		return true;
	}

	@Override
	public boolean isInvalidTarget() {
		return isInvalid();
	}
}
