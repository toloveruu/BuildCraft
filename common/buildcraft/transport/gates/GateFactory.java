/*
 * Copyright (c) SpaceToad, 2011-2012
 * http://www.mod-buildcraft.com
 * 
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.transport.gates;

import buildcraft.api.gates.GateExpansionController;
import buildcraft.api.gates.IGateExpansion;
import buildcraft.transport.Gate;
import buildcraft.transport.Pipe;
import buildcraft.transport.gates.GateDefinition.GateLogic;
import buildcraft.transport.gates.GateDefinition.GateMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 *
 * @author CovertJaguar <http://www.railcraft.info/>
 */
public class GateFactory {

	public static Gate makeGate(Pipe pipe, GateMaterial material, GateLogic logic) {
		return new Gate(pipe, material, logic);
	}

	public static Gate makeGate(Pipe pipe, ItemStack stack) {
		if (stack == null || stack.stackSize <= 0 || !(stack.getItem() instanceof ItemGate))
			return null;

		Gate gate = makeGate(pipe, ItemGate.getMaterial(stack), ItemGate.getLogic(stack));

		for (IGateExpansion expansion : ItemGate.getInstalledExpansions(stack)) {
			gate.expansions.add(expansion.makeController(pipe.container));
		}

		return gate;
	}

	public static Gate makeGate(Pipe pipe, NBTTagCompound nbt) {
		GateMaterial material = GateMaterial.REDSTONE;
		GateLogic logic = GateLogic.AND;

		// Legacy Support
		if (nbt.hasKey("Kind")) {
			int kind = nbt.getInteger("Kind");
			switch (kind) {
				case 1:
				case 2:
					material = GateMaterial.IRON;
					break;
				case 3:
				case 4:
					material = GateMaterial.GOLD;
					break;
				case 5:
				case 6:
					material = GateMaterial.DIAMOND;
					break;
			}
			switch (kind) {
				case 2:
				case 4:
				case 6:
					logic = GateLogic.OR;
					break;
			}
		}

		if (nbt.hasKey("material")) {
			try {
				material = GateMaterial.valueOf(nbt.getString("material"));
			} catch (IllegalArgumentException ex) {
				return null;
			}
		}
		if (nbt.hasKey("logic")) {
			try {
				logic = GateLogic.valueOf(nbt.getString("logic"));
			} catch (IllegalArgumentException ex) {
				return null;
			}
		}

		Gate gate = makeGate(pipe, material, logic);

		if (nbt.hasKey("Pulser")) {
			NBTTagCompound pulsarTag = nbt.getCompoundTag("Pulser");
			GateExpansionController pulsarCon = GateExpansionPulsar.INSTANCE.makeController(pipe.container);
			pulsarCon.readFromNBT(pulsarTag);
			gate.expansions.add(pulsarCon);
		}

		return gate;
	}
}
