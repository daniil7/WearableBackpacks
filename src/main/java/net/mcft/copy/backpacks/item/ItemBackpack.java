package net.mcft.copy.backpacks.item;

import net.mcft.copy.backpacks.WearableBackpacks;
import net.mcft.copy.backpacks.api.BackpackHelper;
import net.mcft.copy.backpacks.api.IBackpack;
import net.mcft.copy.backpacks.api.IBackpackData;
import net.mcft.copy.backpacks.api.IBackpackType;
import net.mcft.copy.backpacks.client.KeyBindingHandler;
import net.mcft.copy.backpacks.container.ContainerBackpack;
import net.mcft.copy.backpacks.misc.BackpackDataItems;
import net.mcft.copy.backpacks.misc.BackpackSize;
import net.mcft.copy.backpacks.misc.util.LangUtils;
import net.mcft.copy.backpacks.misc.util.NbtUtils;
import net.mcft.copy.backpacks.misc.util.WorldUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.commons.lang3.ArrayUtils;

import baubles.api.BaubleType;
import baubles.api.IBauble;

import java.util.List;

// TODO: Implement additional enchantments?
//       - Holding: Increases backpack size (dungeon loot only?)
//       - Supply I: Automatically fills up stackable items from backpack
//       - Supply II: Automatically replaces broken items (and allow middle click to pull from backpack?)
//       - Demand: If a picked up item is stackable and would occupy a new stack in the player's inventory, see
//                 if there's already a non-full stack of it in the backpack, if so pick it up into the backpack.
public class ItemBackpack extends Item implements IBauble, IBackpackType, IDyeableItem {

	// IBauble implementation

	@Override
	public BaubleType getBaubleType(ItemStack itemstack) {
		return BaubleType.BODY;
	}

	@Override
	public boolean canUnequip(ItemStack itemstack, EntityLivingBase player) {
		return false;
	}

	@Override
	public boolean canEquip(ItemStack stack, EntityLivingBase player) {
		return true;
	}

	// END IBauble implementation
	
	public static final int DEFAULT_COLOR = 0xA06540;
	public static final ResourceLocation LOOT_TABLE =
		new ResourceLocation(WearableBackpacks.MOD_ID, "backpack/default");
	
	public static final String[] TAG_CUSTOM_ARMOR = { "backpack", "armor" };
	public static final String[] TAG_CUSTOM_SIZE  = { "backpack", "size" };
	
	public ItemBackpack() {
		super();
		setTranslationKey("wearablebackpacks.backpack");
		setMaxStackSize(1);
		setCreativeTab(CreativeTabs.TOOLS); // TODO: Use our own creative tab?
	}
	
	public float getArmorToughness(ItemStack stack) { return 0; }
	
	// Item properties
	
	@Override
	public boolean getIsRepairable(ItemStack toRepair, ItemStack repair) {
		if (repair.isEmpty()) return false;

		// Returns is the specified repair item is
		// registered in the ore dictionary as leather.
		int leatherOreID = OreDictionary.getOreID("leather");
		return ArrayUtils.contains(OreDictionary.getOreIDs(repair), leatherOreID);
	}
	
	@Override
	public boolean isEnchantable(ItemStack stack) {
		return false;
	}
	
	@Override
	public int getItemEnchantability() { return 12; }
	
	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
		
		IBackpack backpack = BackpackHelper.getBackpack(Minecraft.getMinecraft().player);
		boolean isEquipped = ((backpack != null) && (backpack.getStack() == stack));
		
		// If the shift key is held down, display equip / unequip hints,
		// otherwise just display "Hold SHIFT for more info" message.
		if (LangUtils.tooltipIsShiftKeyDown(tooltip)) {
			boolean enableSelfInteraction = WearableBackpacks.CONFIG.enableSelfInteraction.get();
			
			// If own backpacks can be interacted with while equipped and one is either
			// currently equipped or won't be equipped as boubles body item, display open hint.
			// Does not display anything if key is unbound.
			if (enableSelfInteraction && isEquipped)
				LangUtils.formatTooltipKey(tooltip, "openHint", KeyBindingHandler.openBackpack);
			
			// If the backpack is the player's currently equipped backpack, display unequip hint.
			if (isEquipped) LangUtils.formatTooltip(tooltip, "unequipHint");
			// If not equipped, display the equip hint.
			else LangUtils.formatTooltip(tooltip, "equipHint.extended");
		}
		
		// If someone's using the player's backpack right now, display it in the tooltip.
		if (isEquipped && (backpack.getPlayersUsing() > 0))
			LangUtils.formatTooltipPrepend(tooltip, "\u00A8o", "used");
		
		
		// Only display the following information if advanced tooltips are enabled.
		if (!flagIn.isAdvanced()) return;
		
		NBTBase customSize = NbtUtils.get(stack, TAG_CUSTOM_SIZE);
		if (customSize != null)
			try { tooltip.add("Custom Size: " + BackpackSize.parse(customSize)); }
			catch (Exception ex) {  } // Ignore NBT parse exceptions - they're already logged in createBackpackData.
		
	}
	
	// Item events
	
	@Override
	public EnumActionResult onItemUse(EntityPlayer player, World worldIn, BlockPos pos, EnumHand hand,
	                                  EnumFacing facing, float hitX, float hitY, float hitZ) {
		IBlockState state = worldIn.getBlockState(pos);
		// If the block is replaceable, keep the placing position
		// the same but check the block below for solidity.
		if (state.getBlock().isReplaceable(worldIn, pos))
			state = worldIn.getBlockState(pos.offset(EnumFacing.DOWN));
		// Otherwise make sure the top side is used, and
		// change the placing position to the block above.
		else if (facing == EnumFacing.UP)
			pos = pos.offset(EnumFacing.UP);
		else return EnumActionResult.FAIL;
		
		// Check if the side is solid and try to place the backpack.
		return (state.isSideSolid(worldIn, pos, EnumFacing.UP) &&
		        BackpackHelper.placeBackpack(worldIn, pos, player.getHeldItem(hand), player, false))
			? EnumActionResult.SUCCESS : EnumActionResult.FAIL;
	}
	
	@Override
	public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
		// Allow armor enchantments on top of anything supported by default.
		return (super.canApplyAtEnchantingTable(stack, enchantment));
	}
	
	// IBackpackType implementation
	
	@Override
	public void onSpawnedWith(EntityLivingBase entity, IBackpack backpack, String lootTable) {
	}
	
	@Override
	public void onEquip(EntityLivingBase entity, TileEntity tileEntity, IBackpack backpack) {  }
	
	@Override
	public void onUnequip(EntityLivingBase entity, TileEntity tileEntity, IBackpack backpack) {  }
	
	@Override
	public void onPlacedInteract(EntityPlayer player, TileEntity tileEntity, IBackpack backpack) {
		if (player.world.isRemote) return;
		new ContainerBackpack(player, backpack) {
			@Override public boolean canInteractWith(EntityPlayer player) {
				return player.isEntityAlive()
					&& !tileEntity.isInvalid()
					&& (player.world.getTileEntity(tileEntity.getPos()) == tileEntity)
					&& (player.getDistanceSq(tileEntity.getPos()) <= 64);
			}
		}.open();
	}
	
	@Override
	public void onEquippedInteract(EntityPlayer player, EntityLivingBase target, IBackpack backpack) {
		if (player.world.isRemote) return;
		new ContainerBackpack(player, backpack) {
			@Override public boolean canInteractWith(EntityPlayer player)
				{ return BackpackHelper.canInteractWithEquippedBackpack(player, target); }
		}.open();
	}
	
	@Override
	public void onEquippedTick(EntityLivingBase entity, IBackpack backpack) {  }
	
	@Override
	public void onDeath(EntityLivingBase entity, IBackpack backpack) {
		if (!(backpack.getData() instanceof BackpackDataItems)) return;
		BackpackDataItems dataItems = (BackpackDataItems)backpack.getData();
		WorldUtils.dropStacksFromEntity(entity, dataItems.getItems(entity.world, null), 4.0F);
	}
	
	@Override
	public void onEquippedBroken(EntityLivingBase entity, IBackpack backpack)
		{ onDeath(entity, backpack); }
	
	@Override
	public void onFaultyRemoval(EntityLivingBase entity, IBackpack backpack)
		{ onDeath(entity, backpack); }
	
	@Override
	public void onBlockBreak(TileEntity tileEntity, IBackpack backpack) {
		if (!(backpack.getData() instanceof BackpackDataItems)) return;
		BackpackDataItems dataItems = (BackpackDataItems)backpack.getData();
		WorldUtils.dropStacksFromBlock(tileEntity, dataItems.getItems(tileEntity.getWorld(), null));
	}
	
	@Override
	public IBackpackData createBackpackData(ItemStack stack) {
		BackpackSize size = WearableBackpacks.CONFIG.backpack.size.get();
		
		// Custom size can be specified in stack's NBT data.
		NBTBase customSize = NbtUtils.get(stack, TAG_CUSTOM_SIZE);
		if (customSize != null)
			try { size = BackpackSize.parse(customSize); }
			catch (Exception ex) { WearableBackpacks.LOG.error(
				"Error trying to deserialize backpack size from custom size NBT tag.", ex); }
		
		return new BackpackDataItems(size);
	}
	
	@Override
	public boolean isDamageable()
		{ return !isInvulnurable(); }
	@Override
	public boolean isDamaged(ItemStack stack)
		{ return isInvulnurable() ? false : super.isDamaged(stack); }
	
	@Override
	public int getMaxDamage(ItemStack stack)
		{ return isInvulnurable() ? Short.MAX_VALUE : super.getMaxDamage(stack); }
	@Override
	public int getDamage(ItemStack stack)
		{ return isInvulnurable() ? 0 : super.getDamage(stack); }
	@Override
	public void setDamage(ItemStack stack, int damage) {
		if (isInvulnurable()) damage = 0;
		super.setDamage(stack, damage);
	}
	
	/** Returns if this backpack can't be damaged. */
	private boolean isInvulnurable()
		{ return true; }
}
