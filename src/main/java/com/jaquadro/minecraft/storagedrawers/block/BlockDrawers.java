package com.jaquadro.minecraft.storagedrawers.block;

import com.jaquadro.minecraft.storagedrawers.StorageDrawers;
import com.jaquadro.minecraft.storagedrawers.api.storage.*;
import com.jaquadro.minecraft.storagedrawers.api.storage.attribute.LockAttribute;
import com.jaquadro.minecraft.storagedrawers.block.dynamic.StatusModelData;
import com.jaquadro.minecraft.storagedrawers.block.tile.TileEntityDrawers;
import com.jaquadro.minecraft.storagedrawers.capabilities.CapabilityDrawerAttributes;
import com.jaquadro.minecraft.storagedrawers.core.ModItems;
import com.jaquadro.minecraft.storagedrawers.inventory.DrawerInventoryHelper;
import com.jaquadro.minecraft.storagedrawers.item.*;
import com.jaquadro.minecraft.storagedrawers.security.SecurityManager;
import net.minecraft.block.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.state.BooleanProperty;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootParameters;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public abstract class BlockDrawers extends HorizontalBlock implements INetworked
{
    // TODO: Hold these as properties?
    public static final BooleanProperty ITEM_LOCKED = BooleanProperty.create("item_locked");
    public static final BooleanProperty SHROUDED = BooleanProperty.create("shrouded");
    public static final BooleanProperty VOIDING = BooleanProperty.create("voiding");

    // TODO: TE.getModelData()
    //public static final IUnlistedProperty<DrawerStateModelData> STATE_MODEL = UnlistedModelData.create(DrawerStateModelData.class);

    private static final VoxelShape AABB_FULL = Block.makeCuboidShape(0, 0, 0, 1, 1, 1);
    private static final VoxelShape AABB_NORTH_HALF = Block.makeCuboidShape(0, 0, .5, 1, 1, 1);
    private static final VoxelShape AABB_SOUTH_HALF = Block.makeCuboidShape(0, 0, 0, 1, 1, .5);
    private static final VoxelShape AABB_WEST_HALF = Block.makeCuboidShape(.5, 0, 0, 1, 1, 1);
    private static final VoxelShape AABB_EAST_HALF = Block.makeCuboidShape(0, 0, 0, .5, 1, 1);

    private final int drawerCount;
    private final boolean halfDepth;

    //@SideOnly(Side.CLIENT)
    //private StatusModelData[] statusInfo;

    private long ignoreEventTime;

    private static final ThreadLocal<Boolean> inTileLookup = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue () {
            return false;
        }
    };

    public BlockDrawers (int drawerCount, boolean halfDepth, Block.Properties properties) {
        super(properties);
        this.setDefaultState(stateContainer.getBaseState()
            .with(HORIZONTAL_FACING, Direction.NORTH)
            .with(ITEM_LOCKED, false)
            .with(SHROUDED, false)
            .with(VOIDING, false));

        this.drawerCount = drawerCount;
        this.halfDepth = true;
    }

    public boolean retrimBlock (World world, BlockPos pos, ItemStack prototype) {
        return false;
    }

    public BlockType retrimType () {
        return BlockType.Drawers;
    }

    // TODO: ABSTRACT?  Still need BlockState?
    public int getDrawerCount () {
        return drawerCount;
    }

    public boolean isHalfDepth () {
        return halfDepth;
    }

    @OnlyIn(Dist.CLIENT)
    public void initDynamic () { }

    @OnlyIn(Dist.CLIENT)
    public StatusModelData getStatusInfo (BlockState state) {
        return null;
    }

    @Override
    public BlockRenderLayer getRenderLayer () {
        return BlockRenderLayer.CUTOUT_MIPPED;
    }

    //@Override
    //public boolean canRenderInLayer (IBlockState state, BlockRenderLayer layer) {
    //    return layer == BlockRenderLayer.CUTOUT_MIPPED || layer == BlockRenderLayer.TRANSLUCENT;
    //}


    @Override
    public VoxelShape getShape (BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context) {
        if (!halfDepth)
            return AABB_FULL;

        Direction direction = state.get(HORIZONTAL_FACING);
        switch (direction) {
            case EAST:
                return AABB_EAST_HALF;
            case WEST:
                return AABB_WEST_HALF;
            case SOUTH:
                return AABB_SOUTH_HALF;
            case NORTH:
            default:
                return AABB_NORTH_HALF;
        }
    }

    @Override
    public BlockState getStateForPlacement (BlockItemUseContext context) {
        return this.getDefaultState().with(HORIZONTAL_FACING, context.getPlacementHorizontalFacing().getOpposite());
    }

    /*@Override
    public void onBlockAdded (World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            IBlockState blockNorth = world.getBlockState(pos.north());
            IBlockState blockSouth = world.getBlockState(pos.south());
            IBlockState blockWest = world.getBlockState(pos.west());
            IBlockState blockEast = world.getBlockState(pos.east());

            EnumFacing facing = state.getValue(FACING);

            if (facing == EnumFacing.NORTH && blockNorth.isFullBlock() && !blockSouth.isFullBlock())
                facing = EnumFacing.SOUTH;
            if (facing == EnumFacing.SOUTH && blockSouth.isFullBlock() && !blockNorth.isFullBlock())
                facing = EnumFacing.NORTH;
            if (facing == EnumFacing.WEST && blockWest.isFullBlock() && !blockEast.isFullBlock())
                facing = EnumFacing.EAST;
            if (facing == EnumFacing.EAST && blockEast.isFullBlock() && !blockWest.isFullBlock())
                facing = EnumFacing.WEST;

            TileEntityDrawers tile = getTileEntitySafe(world, pos);
            tile.setDirection(facing.ordinal());
            tile.markDirty();

            world.setBlockState(pos, state.withProperty(FACING, facing));
        }

        super.onBlockAdded(world, pos, state);
    }*/

    @Override
    public void onBlockPlacedBy (World world, BlockPos pos, BlockState state, LivingEntity entity, ItemStack stack) {
        if (stack.hasDisplayName()) {
            TileEntityDrawers tile = getTileEntity(world, pos);
            if (tile != null)
                tile.setCustomName(stack.getDisplayName());
        }

        if (entity.getHeldItemOffhand().getItem() == ModItems.drawerKey) {
            TileEntityDrawers tile = getTileEntity(world, pos);
            if (tile != null) {
                IDrawerAttributes _attrs = tile.getCapability(CapabilityDrawerAttributes.DRAWER_ATTRIBUTES_CAPABILITY).orElse(new EmptyDrawerAttributes());
                if (_attrs instanceof IDrawerAttributesModifiable) {
                    IDrawerAttributesModifiable attrs = (IDrawerAttributesModifiable) _attrs;
                    attrs.setItemLocked(LockAttribute.LOCK_EMPTY, true);
                    attrs.setItemLocked(LockAttribute.LOCK_POPULATED, true);
                }
            }
        }
    }

    @Override
    public boolean isReplaceable (BlockState state, BlockItemUseContext useContext) {
        if (useContext.getPlayer().isCreative()) {
            double blockReachDistance = useContext.getPlayer().getAttribute(PlayerEntity.REACH_DISTANCE).getValue() + 1;
            BlockRayTraceResult result = rayTraceEyes(useContext.getWorld(), useContext.getPlayer(), blockReachDistance);

            if (result.getType() == RayTraceResult.Type.MISS || result.getFace() != state.get(HORIZONTAL_FACING))
                useContext.getWorld().setBlockState(useContext.getPos(), Blocks.AIR.getDefaultState(), useContext.getWorld().isRemote ? 11 : 3);
            else
                onBlockClicked(state, useContext.getWorld(), useContext.getPos(), useContext.getPlayer());

            return false;
        }

        return super.isReplaceable(state, useContext);
    }

    @Override
    public boolean onBlockActivated (BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult hit) {
        ItemStack item = player.getHeldItem(hand);
        if (hand == Hand.OFF_HAND)
            return false;

        if (world.isRemote && Util.milliTime() == ignoreEventTime) {
            ignoreEventTime = 0;
            return false;
        }

        TileEntityDrawers tileDrawers = getTileEntitySafe(world, pos);

        if (!SecurityManager.hasAccess(player.getGameProfile(), tileDrawers))
            return false;

        //if (StorageDrawers.config.cache.debugTrace) {
            StorageDrawers.log.info("BlockDrawers.onBlockActivated");
            StorageDrawers.log.info((item.isEmpty()) ? "  null item" : "  " + item.toString());
        //}

        /*
        if (!item.isEmpty()) {
            if (item.getItem() instanceof ItemKey)
                return false;

            if (item.getItem() instanceof ItemTrim && player.isSneaking()) {
                if (!retrimBlock(world, pos, item))
                    return false;

                if (!player.capabilities.isCreativeMode) {
                    item.shrink(1);
                    if (item.getCount() <= 0)
                        player.inventory.setInventorySlotContents(player.inventory.currentItem, ItemStack.EMPTY);
                }

                return true;
            }
            else if (item.getItem() instanceof ItemUpgrade) {
                if (!tileDrawers.upgrades().canAddUpgrade(item)) {
                    if (!world.isRemote)
                        player.sendStatusMessage(new TextComponentTranslation("storagedrawers.msg.cannotAddUpgrade"), true);

                    return false;
                }

                if (!tileDrawers.upgrades().addUpgrade(item)) {
                    if (!world.isRemote)
                        player.sendStatusMessage(new TextComponentTranslation("storagedrawers.msg.maxUpgrades"), true);

                    return false;
                }

                world.notifyBlockUpdate(pos, state, state, 3);

                if (!player.capabilities.isCreativeMode) {
                    item.shrink(1);
                    if (item.getCount() <= 0)
                        player.inventory.setInventorySlotContents(player.inventory.currentItem, ItemStack.EMPTY);
                }

                return true;
            }
            else if (item.getItem() instanceof ItemPersonalKey) {
                String securityKey = ((ItemPersonalKey) item.getItem()).getSecurityProviderKey(item.getItemDamage());
                ISecurityProvider provider = StorageDrawers.securityRegistry.getProvider(securityKey);

                if (tileDrawers.getOwner() == null) {
                    tileDrawers.setOwner(player.getPersistentID());
                    tileDrawers.setSecurityProvider(provider);
                }
                else if (SecurityManager.hasOwnership(player.getGameProfile(), tileDrawers)) {
                    tileDrawers.setOwner(null);
                    tileDrawers.setSecurityProvider(null);
                }
                else
                    return false;
                return true;
            }
            else if (item.getItem() == ModItems.tape)
                return false;
        }
        else if (item.isEmpty() && player.isSneaking()) {
            if (tileDrawers.isSealed()) {
                tileDrawers.setIsSealed(false);
                return true;
            }
            else if (StorageDrawers.config.cache.enableDrawerUI) {
                player.openGui(StorageDrawers.instance, GuiHandler.drawersGuiID, world, pos.getX(), pos.getY(), pos.getZ());
                return true;
            }
        }*/

        if (state.get(HORIZONTAL_FACING) != hit.getFace())
            return false;

        //if (tileDrawers.isSealed())
        //    return false;

        int slot = getDrawerSlot(hit);
        tileDrawers.interactPutItemsIntoSlot(slot, player);

        if (item.isEmpty())
            player.setHeldItem(hand, ItemStack.EMPTY);

        return true;
    }

    protected final int getDrawerSlot (BlockRayTraceResult hit) {
        Vec3d hitVec = hit.getHitVec();
        return getDrawerSlot(hit.getFace(), hitVec.x, hitVec.y, hitVec.z);
    }

    protected int getDrawerSlot (Direction side, double hitX, double hitY, double hitZ) {
        return 0;
    }

    protected boolean hitTop (double hitY) {
        return hitY > .5;
    }

    protected boolean hitLeft (Direction side, double hitX, double hitZ) {
        switch (side) {
            case NORTH:
                return hitX > .5;
            case SOUTH:
                return hitX < .5;
            case WEST:
                return hitZ < .5;
            case EAST:
                return hitZ > .5;
            default:
                return true;
        }
    }

    protected BlockRayTraceResult rayTraceEyes(World world, PlayerEntity player, double length) {
        Vec3d startPos = new Vec3d(player.posX, player.posY, player.posZ);
        Vec3d endPos = startPos.add(player.getLookVec().x * length, player.getLookVec().y * length, player.getLookVec().z * length);
        RayTraceContext context = new RayTraceContext(startPos, endPos, RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.NONE, player);
        return world.rayTraceBlocks(context);
    }

    @Override
    public void onBlockClicked(BlockState state, World worldIn, BlockPos pos, PlayerEntity playerIn) {
        if (worldIn.isRemote)
            return;

        //if (StorageDrawers.config.cache.debugTrace)
            StorageDrawers.log.info("onBlockClicked");
        BlockRayTraceResult rayResult = rayTraceEyes(worldIn, playerIn, playerIn.getAttribute(PlayerEntity.REACH_DISTANCE).getValue() + 1);
        if (rayResult.getType() == RayTraceResult.Type.MISS)
            return;

        Direction side = rayResult.getFace();
        Vec3d hitVec = rayResult.getHitVec();

        // adjust hitVec for drawers
        float hitX = (float)(hitVec.x - pos.getX());
        float hitY = (float)(hitVec.y - pos.getY());
        float hitZ = (float)(hitVec.z - pos.getZ());

        TileEntityDrawers tileDrawers = getTileEntitySafe(worldIn, pos);
        if (state.get(HORIZONTAL_FACING) != side)
            return;

        //if (tileDrawers.isSealed())
        //    return;

        //if (!SecurityManager.hasAccess(playerIn.getGameProfile(), tileDrawers))
        //    return;

        int slot = getDrawerSlot(side, hitX, hitY, hitZ);
        IDrawer drawer = tileDrawers.getDrawer(slot);

        ItemStack item;
        //Map<String, PlayerConfigSetting<?>> configSettings = ConfigManager.serverPlayerConfigSettings.get(playerIn.getUniqueID());
        boolean invertShift = false;
        /*if (configSettings != null) {
            PlayerConfigSetting<Boolean> setting = (PlayerConfigSetting<Boolean>) configSettings.get("invertShift");
            if (setting != null) {
                invertShift = setting.value;
            }
        }*/
        if (playerIn.isSneaking() != invertShift)
            item = tileDrawers.takeItemsFromSlot(slot, drawer.getStoredItemStackSize());
        else
            item = tileDrawers.takeItemsFromSlot(slot, 1);

        //if (StorageDrawers.config.cache.debugTrace)
            StorageDrawers.log.info((item.isEmpty()) ? "  null item" : "  " + item.toString());

        if (!item.isEmpty()) {
            if (!playerIn.inventory.addItemStackToInventory(item)) {
                dropItemStack(worldIn, pos.offset(side), playerIn, item);
                worldIn.notifyBlockUpdate(pos, state, state, 3);
            }
            else
                worldIn.playSound(null, pos.getX() + .5f, pos.getY() + .5f, pos.getZ() + .5f, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, .2f, ((worldIn.rand.nextFloat() - worldIn.rand.nextFloat()) * .7f + 1) * 2);
        }
    }

    private void dropItemStack (World world, BlockPos pos, PlayerEntity player, @Nonnull ItemStack stack) {
        ItemEntity entity = new ItemEntity(world, pos.getX() + .5f, pos.getY() + .3f, pos.getZ() + .5f, stack);
        Vec3d motion = entity.getMotion();
        entity.addVelocity(-motion.x, -motion.y, -motion.z);
        world.addEntity(entity);
    }



    @Override
    public void onReplaced (BlockState state, World world, BlockPos pos, BlockState newState, boolean isMoving) {
        TileEntityDrawers tile = getTileEntity(world, pos);

        if (tile != null) {
            for (int i = 0; i < tile.upgrades().getSlotCount(); i++) {
                ItemStack stack = tile.upgrades().getUpgrade(i);
                if (!stack.isEmpty()) {
                    if (stack.getItem() instanceof ItemUpgradeCreative)
                        continue;
                    spawnAsEntity(world, pos, stack);
                }
            }

            if (!tile.getDrawerAttributes().isUnlimitedVending())
                DrawerInventoryHelper.dropInventoryItems(world, pos, tile.getGroup());
        }

        super.onReplaced(state, world, pos, newState, isMoving);
    }

    @Override
    public List<ItemStack> getDrops (BlockState state, LootContext.Builder builder) {
        List<ItemStack> items = new ArrayList<>();
        items.add(getMainDrop(state, (TileEntityDrawers)builder.get(LootParameters.BLOCK_ENTITY)));
        return items;
    }

    protected ItemStack getMainDrop (BlockState state, TileEntityDrawers tile) {
        ItemStack drop = new ItemStack(this);
        if (tile == null)
            return drop;

        CompoundNBT data = drop.getTag();
        if (data == null)
            data = new CompoundNBT();

        //if (tile.isSealed()) {
            CompoundNBT tiledata = new CompoundNBT();
            tile.write(tiledata);
            data.put("tile", tiledata);
        //}

        drop.setTag(data);
        return drop;
    }

    /*@Override
    public float getExplosionResistance (World world, BlockPos pos, Entity exploder, Explosion explosion) {
        TileEntityDrawers tile = getTileEntity(world, pos);
        if (tile != null) {
            for (int slot = 0; slot < 5; slot++) {
                ItemStack stack = tile.upgrades().getUpgrade(slot);
                if (stack.isEmpty() || !(stack.getItem() instanceof ItemUpgradeStorage))
                    continue;

                if (EnumUpgradeStorage.byMetadata(stack.getMetadata()) != EnumUpgradeStorage.OBSIDIAN)
                    continue;

                return 1000;
            }
        }

        return super.getExplosionResistance(world, pos, exploder, explosion);
    }*/

    public TileEntityDrawers getTileEntity (IBlockReader blockAccess, BlockPos pos) {
        if (inTileLookup.get())
            return null;

        inTileLookup.set(true);
        TileEntity tile = blockAccess.getTileEntity(pos);
        inTileLookup.set(false);

        return (tile instanceof TileEntityDrawers) ? (TileEntityDrawers) tile : null;
    }

    public TileEntityDrawers getTileEntitySafe (World world, BlockPos pos) {
        TileEntityDrawers tile = getTileEntity(world, pos);
        if (tile == null) {
            tile = (TileEntityDrawers) createTileEntity(world.getBlockState(pos), world);
            world.setTileEntity(pos, tile);
        }

        return tile;
    }

    /*@Override
    @SideOnly(Side.CLIENT)
    public boolean addHitEffects (IBlockState state, World worldObj, RayTraceResult target, ParticleManager manager) {
        if (getDirection(worldObj, target.getBlockPos()) == target.sideHit)
            return true;

        return super.addHitEffects(state, worldObj, target, manager);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean addDestroyEffects (World world, BlockPos pos, ParticleManager manager) {
        //TileEntityDrawers tile = getTileEntity(world, pos);
        //if (tile != null && !tile.getWillDestroy())
        //    return true;

        return super.addDestroyEffects(world, pos, manager);
    }*/

    @Override
    @SuppressWarnings("deprecation")
    public boolean canProvidePower (BlockState state) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getWeakPower (BlockState state, IBlockReader blockAccess, BlockPos pos, Direction side) {
        if (!canProvidePower(state))
            return 0;

        TileEntityDrawers tile = getTileEntity(blockAccess, pos);
        if (tile == null || !tile.isRedstone())
            return 0;

        return tile.getRedstoneLevel();
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getStrongPower (BlockState state, IBlockReader worldIn, BlockPos pos, Direction side) {
        return (side == Direction.UP) ? getWeakPower(state, worldIn, pos, side) : 0;
    }
}