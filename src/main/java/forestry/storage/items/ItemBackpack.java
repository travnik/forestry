package forestry.storage.items;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import forestry.api.core.ForestryAPI;
import forestry.api.storage.BackpackStowEvent;
import forestry.api.storage.EnumBackpackType;
import forestry.api.storage.IBackpackDefinition;
import forestry.core.config.Config;
import forestry.core.inventory.InvTools;
import forestry.core.inventory.ItemInventory;
import forestry.core.inventory.ItemInventoryBackpack;
import forestry.core.inventory.wrappers.IInvSlot;
import forestry.core.inventory.wrappers.InventoryIterator;
import forestry.core.items.ItemInventoried;
import forestry.core.network.GuiId;
import forestry.core.proxy.Proxies;
import forestry.core.render.TextureManager;
import forestry.core.utils.StringUtil;
import forestry.storage.BackpackMode;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import com.gamerforea.eventhelper.util.EventUtils;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;

public class ItemBackpack extends ItemInventoried {

   private final IBackpackDefinition definition;
   private final EnumBackpackType type;
   @SideOnly(Side.CLIENT)
   private IIcon[] icons;


   public ItemBackpack(IBackpackDefinition definition, EnumBackpackType type) {
      this.definition = definition;
      this.type = type;
      this.setMaxStackSize(1);
   }

   public IBackpackDefinition getDefinition() {
      return this.definition;
   }

   public boolean getShareTag() {
      return true;
   }

   public ItemStack onItemRightClick(ItemStack itemstack, World world, EntityPlayer player) {
      if(!Proxies.common.isSimulating(world)) {
         return itemstack;
      } else {
         if(!player.isSneaking()) {
            this.openGui(player, itemstack);
         } else {
            switchMode(itemstack);
         }

         return itemstack;
      }
   }

   public boolean onItemUse(ItemStack itemstack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ) {
      return EventUtils.cantBreak(player, x, y, z) ? false : getInventoryHit(world, x, y, z, side) != null;
   }

   public boolean onItemUseFirst(ItemStack itemstack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ) {
      return !Proxies.common.isSimulating(world) ? false : 
    	  (EventUtils.cantBreak(player, x, y, z) || !player.isSneaking() ? false : this.evaluateTileHit(itemstack, player, world, x, y, z, side, hitX, hitY, hitZ));
   }

   public static ItemStack tryStowing(EntityPlayer player, ItemStack backpackStack, ItemStack stack) {
      ItemBackpack backpack = (ItemBackpack)backpackStack.getItem();
      ItemInventoryBackpack inventory = new ItemInventoryBackpack(player, backpack.getBackpackSize(), backpackStack);
      if(backpackStack.getItemDamage() == 1) {
         return stack;
      } else {
         BackpackStowEvent event = new BackpackStowEvent(player, backpack.getDefinition(), inventory, stack);
         MinecraftForge.EVENT_BUS.post(event);
         if(stack.stackSize <= 0) {
            return null;
         } else if(event.isCanceled()) {
            return stack;
         } else {
            ItemStack remainder = InvTools.moveItemStack(stack, (IInventory)inventory);
            stack.stackSize = remainder == null?0:remainder.stackSize;
            return null;
         }
      }
   }

   private static void switchMode(ItemStack itemstack) {
      BackpackMode mode = getMode(itemstack);
      int nextMode = mode.ordinal() + 1;
      if(!Config.enableBackpackResupply && nextMode == BackpackMode.RESUPPLY.ordinal()) {
         ++nextMode;
      }

      nextMode %= BackpackMode.values().length;
      itemstack.setItemDamage(nextMode);
   }

   private static IInventory getInventoryHit(World world, int x, int y, int z, int side) {
      TileEntity targeted = world.getTileEntity(x, y, z);
      return InvTools.getInventoryFromTile(targeted, ForgeDirection.getOrientation(side));
   }

   private boolean evaluateTileHit(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ) {
	   if(EventUtils.cantBreak(player, x, y, z)){
		   return false;
	   }
      IInventory inventory = getInventoryHit(world, x, y, z, side);
      if(inventory != null) {
         if(inventory.getSizeInventory() <= 0) {
            return true;
         } else {
            ItemInventoryBackpack backpackInventory = new ItemInventoryBackpack(player, this.getBackpackSize(), stack);
            BackpackMode mode = getMode(stack);
            if(mode == BackpackMode.RECEIVE) {
               this.tryChestReceive(backpackInventory, inventory);
            } else {
               tryChestTransfer(backpackInventory, inventory);
            }

            return true;
         }
      } else {
         return false;
      }
   }

   private static void tryChestTransfer(ItemInventoryBackpack backpackInventory, IInventory target) {
      Iterator i$ = InventoryIterator.getIterable(backpackInventory).iterator();

      while(i$.hasNext()) {
         IInvSlot slot = (IInvSlot)i$.next();
         ItemStack packStack = slot.getStackInSlot();
         if(packStack != null) {
            ItemStack remaining = InvTools.moveItemStack(packStack, target);
            slot.setStackInSlot(remaining);
         }
      }

   }

   private void tryChestReceive(ItemInventoryBackpack backpackInventory, IInventory target) {
      Iterator i$ = InventoryIterator.getIterable(target).iterator();

      while(i$.hasNext()) {
         IInvSlot slot = (IInvSlot)i$.next();
         ItemStack targetStack = slot.getStackInSlot();
         if(targetStack != null && this.definition.isValidItem(targetStack)) {
            ItemStack remaining = InvTools.moveItemStack(targetStack, (IInventory)backpackInventory);
            slot.setStackInSlot(remaining);
         }
      }

   }

   public void openGui(EntityPlayer entityplayer, ItemStack itemstack) {
      if(this.getBackpackSize() == 15) {
         entityplayer.openGui(ForestryAPI.instance, GuiId.BackpackGUI.ordinal(), entityplayer.worldObj, (int)entityplayer.posX, (int)entityplayer.posY, (int)entityplayer.posZ);
      } else if(this.getBackpackSize() == 45) {
         entityplayer.openGui(ForestryAPI.instance, GuiId.BackpackT2GUI.ordinal(), entityplayer.worldObj, (int)entityplayer.posX, (int)entityplayer.posY, (int)entityplayer.posZ);
      }

   }

   public int getBackpackSize() {
      return getSlotsForType(this.type);
   }

   public void func_77624_a(ItemStack itemstack, EntityPlayer player, List list, boolean flag) {
      int occupied = ItemInventory.getOccupiedSlotCount(itemstack);
      BackpackMode mode = getMode(itemstack);
      if(mode == BackpackMode.LOCKED) {
         list.add(StringUtil.localize("storage.backpack.mode.locked"));
      } else if(mode == BackpackMode.RECEIVE) {
         list.add(StringUtil.localize("storage.backpack.mode.receiving"));
      } else if(mode == BackpackMode.RESUPPLY) {
         list.add(StringUtil.localize("storage.backpack.mode.resupply"));
      }

      list.add(StringUtil.localize("gui.slots").replaceAll("%USED", String.valueOf(occupied)).replaceAll("%SIZE", String.valueOf(this.getBackpackSize())));
   }

   public String func_77653_i(ItemStack itemstack) {
      return this.definition.getName(itemstack);
   }

   @SideOnly(Side.CLIENT)
   public void func_94581_a(IIconRegister register) {
      this.icons = new IIcon[6];
      EnumBackpackType t = this.type == EnumBackpackType.APIARIST?EnumBackpackType.T1:this.type;
      String typeTag = "backpacks/" + t.toString().toLowerCase(Locale.ENGLISH);
      this.icons[0] = TextureManager.getInstance().registerTex(register, typeTag + ".cloth");
      this.icons[1] = TextureManager.getInstance().registerTex(register, typeTag + ".outline");
      this.icons[2] = TextureManager.getInstance().registerTex(register, "backpacks/neutral");
      this.icons[3] = TextureManager.getInstance().registerTex(register, "backpacks/locked");
      this.icons[4] = TextureManager.getInstance().registerTex(register, "backpacks/receive");
      this.icons[5] = TextureManager.getInstance().registerTex(register, "backpacks/resupply");
   }

   public boolean func_77623_v() {
      return true;
   }

   public int getRenderPasses(int metadata) {
      return 3;
   }

   public int func_82790_a(ItemStack itemstack, int j) {
      return j == 0?this.definition.getPrimaryColour():(j == 1?this.definition.getSecondaryColour():16777215);
   }

   @SideOnly(Side.CLIENT)
   public IIcon func_77618_c(int i, int j) {
      return j == 0?this.icons[0]:(j == 1?this.icons[1]:(i > 2?this.icons[5]:(i > 1?this.icons[4]:(i > 0?this.icons[3]:this.icons[2]))));
   }

   public static int getSlotsForType(EnumBackpackType type) {
      switch(ItemBackpack.NamelessClass28805444.$SwitchMap$forestry$api$storage$EnumBackpackType[type.ordinal()]) {
      case 1:
         return 125;
      case 2:
         return 45;
      case 3:
      default:
         return 15;
      }
   }

   public static BackpackMode getMode(ItemStack backpack) {
      int meta = backpack.getItemDamage();
      return meta >= 3?BackpackMode.RESUPPLY:(meta >= 2?BackpackMode.RECEIVE:(meta >= 1?BackpackMode.LOCKED:BackpackMode.NORMAL));
   }

   // $FF: synthetic class
   static class NamelessClass28805444 {

      // $FF: synthetic field
      static final int[] $SwitchMap$forestry$api$storage$EnumBackpackType = new int[EnumBackpackType.values().length];


      static {
         try {
            $SwitchMap$forestry$api$storage$EnumBackpackType[EnumBackpackType.APIARIST.ordinal()] = 1;
         } catch (NoSuchFieldError var3) {
            ;
         }

         try {
            $SwitchMap$forestry$api$storage$EnumBackpackType[EnumBackpackType.T2.ordinal()] = 2;
         } catch (NoSuchFieldError var2) {
            ;
         }

         try {
            $SwitchMap$forestry$api$storage$EnumBackpackType[EnumBackpackType.T1.ordinal()] = 3;
         } catch (NoSuchFieldError var1) {
            ;
         }

      }
   }
}
