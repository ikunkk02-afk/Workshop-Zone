package io.github.ikunkk02afk.workshopzone.search;

import io.github.ikunkk02afk.workshopzone.label.ContainerLabelEntry;
import io.github.ikunkk02afk.workshopzone.label.ContainerLabelRule;
import io.github.ikunkk02afk.workshopzone.label.ContainerLabelService;
import io.github.ikunkk02afk.workshopzone.label.LogicalContainer;
import io.github.ikunkk02afk.workshopzone.label.SilentContainerAccess;
import io.github.ikunkk02afk.workshopzone.label.WorkshopContainerResolver;
import io.github.ikunkk02afk.workshopzone.network.RequestWorkshopItemCatalogPayload;
import io.github.ikunkk02afk.workshopzone.network.SearchWorkshopItemPayload;
import io.github.ikunkk02afk.workshopzone.network.WorkshopItemCatalogPayload;
import io.github.ikunkk02afk.workshopzone.network.WorkshopItemSearchResultPayload;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import io.github.ikunkk02afk.workshopzone.session.WorkshopOpenResult;
import io.github.ikunkk02afk.workshopzone.session.WorkshopSession;
import io.github.ikunkk02afk.workshopzone.session.WorkshopSessionManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.TrappedChestBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.inventory.ContainerLock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import net.minecraft.potion.Potions;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.block.enums.ChestType;

import java.util.List;

public final class WorkshopItemSearchGameTests implements FabricGameTest {
	private static final BlockPos BASE = new BlockPos(1, 1, 1);

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void catalogOnlyContainsActualItemsAndDoesNotMutateInventories(TestContext context) {
		LogicalContainer chest = place(context, BASE, Blocks.CHEST.getDefaultState());
		chest.inventory().setStack(0, new ItemStack(Items.IRON_INGOT, 64));
		chest.inventory().setStack(1, new ItemStack(Items.GOLD_INGOT, 32));
		SessionFixture fixture = openSession(context, chest);
		int before = chest.inventory().count(Items.IRON_INGOT) + chest.inventory().count(Items.GOLD_INGOT);

		WorkshopItemCatalogPayload result = catalog(fixture, new WorkshopItemCatalogService(fixture.manager()), 101);

		context.assertEquals(WorkshopItemCatalogResultCode.SUCCESS, result.resultId(), "Catalog should load successfully");
		context.assertEquals(List.of(Identifier.ofVanilla("iron_ingot"), Identifier.ofVanilla("gold_ingot")),
			result.entries().stream().map(WorkshopItemCatalogEntry::itemId).toList(), "Catalog should contain only stored item types");
		context.assertTrue(result.entries().stream().noneMatch(entry -> entry.itemId().equals(Identifier.ofVanilla("iron_chestplate"))),
			"An absent iron chestplate must not appear in the catalog");
		context.assertEquals(before, chest.inventory().count(Items.IRON_INGOT) + chest.inventory().count(Items.GOLD_INGOT),
			"Catalog generation must not mutate inventory contents");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void catalogRefreshReflectsAddedAndRemovedItemTypes(TestContext context) {
		LogicalContainer chest = place(context, BASE, Blocks.CHEST.getDefaultState());
		chest.inventory().setStack(0, new ItemStack(Items.IRON_INGOT, 64));
		SessionFixture fixture = openSession(context, chest);
		WorkshopItemCatalogPayload initial = catalog(fixture, new WorkshopItemCatalogService(fixture.manager()), 102);
		context.assertTrue(initial.entries().stream().noneMatch(entry -> entry.itemId().equals(Identifier.ofVanilla("iron_chestplate"))),
			"Absent chestplate should not be present initially");

		chest.inventory().setStack(1, new ItemStack(Items.IRON_CHESTPLATE));
		WorkshopItemCatalogPayload added = catalog(fixture, new WorkshopItemCatalogService(fixture.manager()), 103);
		context.assertTrue(added.entries().stream().anyMatch(entry -> entry.itemId().equals(Identifier.ofVanilla("iron_chestplate"))),
			"Added chestplate should appear after a fresh catalog request");

		chest.inventory().setStack(1, ItemStack.EMPTY);
		WorkshopItemCatalogPayload removed = catalog(fixture, new WorkshopItemCatalogService(fixture.manager()), 104);
		context.assertTrue(removed.entries().stream().noneMatch(entry -> entry.itemId().equals(Identifier.ofVanilla("iron_chestplate"))),
			"Removed chestplate should disappear after a fresh catalog request");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void catalogAggregatesTwoLogicalContainersAndDeduplicatesDoubleChest(TestContext context) {
		LogicalContainer doubleChest = doubleChest(context, BASE);
		LogicalContainer single = place(context, BASE.add(4, 0, 0), Blocks.CHEST.getDefaultState());
		doubleChest.inventory().setStack(0, new ItemStack(Items.IRON_INGOT, 32));
		doubleChest.inventory().setStack(53, new ItemStack(Items.IRON_INGOT, 16));
		single.inventory().setStack(0, new ItemStack(Items.IRON_INGOT, 64));
		SessionFixture fixture = openSession(context, doubleChest);

		WorkshopItemCatalogEntry iron = catalog(fixture, new WorkshopItemCatalogService(fixture.manager()), 105)
			.entries().stream().filter(entry -> entry.itemId().equals(Identifier.ofVanilla("iron_ingot"))).findFirst().orElseThrow();

		context.assertEquals(112L, iron.totalCount(), "All iron stacks should be summed with long arithmetic");
		context.assertEquals(2, iron.matchingContainerCount(), "A double chest must count as one logical container");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void catalogIncludesBarrelTrappedChestAndLabeledContainers(TestContext context) {
		LogicalContainer barrel = place(context, BASE, Blocks.BARREL.getDefaultState());
		LogicalContainer trapped = place(context, BASE.add(3, 0, 0), Blocks.TRAPPED_CHEST.getDefaultState());
		LogicalContainer labeled = place(context, BASE.add(6, 0, 0), Blocks.CHEST.getDefaultState());
		ContainerLabelService.applyAtomically(labeled, ContainerLabelRule.exact(Items.IRON_INGOT));
		barrel.inventory().setStack(0, new ItemStack(Items.IRON_INGOT, 1));
		trapped.inventory().setStack(0, new ItemStack(Items.IRON_INGOT, 2));
		labeled.inventory().setStack(0, new ItemStack(Items.IRON_INGOT, 3));
		SessionFixture fixture = openSession(context, barrel);

		WorkshopItemCatalogEntry iron = catalog(fixture, new WorkshopItemCatalogService(fixture.manager()), 106)
			.entries().stream().filter(entry -> entry.itemId().equals(Identifier.ofVanilla("iron_ingot"))).findFirst().orElseThrow();

		context.assertEquals(6L, iron.totalCount(), "Supported container kinds and labels should preserve counts");
		context.assertEquals(3, iron.matchingContainerCount(), "Barrel, trapped chest, and labeled chest should all be counted");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void catalogDoesNotLeakLockedOrCallbackDeniedItems(TestContext context) {
		LogicalContainer opened = place(context, BASE, Blocks.CHEST.getDefaultState());
		LogicalContainer locked = place(context, BASE.add(3, 0, 0), Blocks.CHEST.getDefaultState());
		LogicalContainer denied = place(context, BASE.add(6, 0, 0), Blocks.CHEST.getDefaultState());
		opened.inventory().setStack(0, new ItemStack(Items.GOLD_INGOT, 1));
		locked.inventory().setStack(0, new ItemStack(Items.DIAMOND, 64));
		denied.inventory().setStack(0, new ItemStack(Items.NETHERITE_SCRAP, 32));
		ChestBlockEntity blockEntity = (ChestBlockEntity)context.getWorld().getBlockEntity(locked.representativePosition());
		ItemStack lockedChestItem = new ItemStack(Items.CHEST);
		lockedChestItem.set(DataComponentTypes.LOCK, new ContainerLock("secret"));
		blockEntity.readComponents(lockedChestItem);
		SessionFixture fixture = openSession(context, opened);
		WorkshopContainerAccessService access = new WorkshopContainerAccessService(
			(player, world, entry, state) -> true,
			(player, world, container, targetItem) -> targetItem != Items.NETHERITE_SCRAP
		);

		WorkshopItemCatalogPayload result = catalog(
			fixture, new WorkshopItemCatalogService(fixture.manager(), access), 107
		);

		context.assertEquals(List.of(Identifier.ofVanilla("gold_ingot")),
			result.entries().stream().map(WorkshopItemCatalogEntry::itemId).toList(),
			"Locked and callback-denied contents must not be disclosed");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void catalogWithOnlyDeniedContentsIsIndistinguishableFromEmpty(TestContext context) {
		LogicalContainer chest = place(context, BASE, Blocks.CHEST.getDefaultState());
		chest.inventory().setStack(0, new ItemStack(Items.DIAMOND, 64));
		SessionFixture fixture = openSession(context, chest);
		WorkshopContainerAccessService access = new WorkshopContainerAccessService(
			(player, world, entry, state) -> true,
			(player, world, container, targetItem) -> false
		);

		WorkshopItemCatalogPayload result = catalog(
			fixture, new WorkshopItemCatalogService(fixture.manager(), access), 111
		);

		context.assertEquals(WorkshopItemCatalogResultCode.EMPTY, result.resultId(),
			"Denied contents must not be distinguishable from an empty visible catalog");
		context.assertTrue(result.entries().isEmpty(), "Denied contents must not enter the catalog");

		ChestBlockEntity blockEntity = (ChestBlockEntity)context.getWorld().getBlockEntity(chest.representativePosition());
		ItemStack lockedChestItem = new ItemStack(Items.CHEST);
		lockedChestItem.set(DataComponentTypes.LOCK, new ContainerLock("secret"));
		blockEntity.readComponents(lockedChestItem);
		WorkshopItemCatalogPayload lockedResult = catalog(
			fixture, new WorkshopItemCatalogService(fixture.manager()), 112
		);
		context.assertEquals(WorkshopItemCatalogResultCode.EMPTY, lockedResult.resultId(),
			"Locked contents must be indistinguishable from an empty visible catalog");
		context.assertTrue(lockedResult.entries().isEmpty(), "Locked contents must not enter the catalog");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void repeatedCatalogRequestsUseIndependentTenTickCooldownPerPlayer(TestContext context) {
		LogicalContainer chest = place(context, BASE, Blocks.CHEST.getDefaultState());
		chest.inventory().setStack(0, new ItemStack(Items.IRON_INGOT, 1));
		SessionFixture first = openSession(context, chest);
		SessionFixture second = openSession(context, chest);
		WorkshopItemCatalogService service = new WorkshopItemCatalogService(first.manager());

		WorkshopItemCatalogPayload firstResult = catalog(first, service, 108);
		WorkshopItemCatalogPayload repeated = catalog(first, service, 109);
		WorkshopItemCatalogPayload secondPlayer = catalog(second, service, 110);

		context.assertEquals(WorkshopItemCatalogResultCode.SUCCESS, firstResult.resultId(), "First catalog request should run");
		context.assertEquals(WorkshopItemCatalogResultCode.COOLDOWN, repeated.resultId(), "Immediate repeat should be throttled");
		context.assertEquals(WorkshopItemCatalogResultCode.SUCCESS, secondPlayer.resultId(), "Another player should have an independent cooldown");
		finish(first, context);
		finish(second, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void singleChestSearchCountsSixtyFourWithoutMutation(TestContext context) {
		LogicalContainer chest = place(context, BASE, Blocks.CHEST.getDefaultState());
		chest.inventory().setStack(0, new ItemStack(Items.IRON_INGOT, 64));
		SessionFixture fixture = openSession(context, chest);
		WorkshopItemSearchResultPayload result = search(fixture, Items.IRON_INGOT, new WorkshopItemSearchService(fixture.manager()), 1);
		context.assertEquals(WorkshopItemSearchResultCode.SUCCESS, result.resultId(), "Single chest search should succeed");
		context.assertEquals(64L, result.totalItemCount(), "Single chest should report 64 iron ingots");
		context.assertEquals(1, result.totalMatchingContainers(), "Single chest should produce one result");
		context.assertEquals(64, chest.inventory().count(Items.IRON_INGOT), "Search must not mutate the chest");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void twoChestsAreAggregatedAndSortedByDistance(TestContext context) {
		LogicalContainer near = place(context, BASE, Blocks.CHEST.getDefaultState());
		LogicalContainer far = place(context, BASE.add(3, 0, 0), Blocks.CHEST.getDefaultState());
		near.inventory().setStack(0, new ItemStack(Items.IRON_INGOT, 32));
		far.inventory().setStack(0, new ItemStack(Items.IRON_INGOT, 32));
		SessionFixture fixture = openSession(context, near);
		WorkshopItemSearchResultPayload result = search(fixture, Items.IRON_INGOT, new WorkshopItemSearchService(fixture.manager()), 2);
		context.assertEquals(64L, result.totalItemCount(), "Two chests should total 64 iron ingots");
		context.assertEquals(2, result.totalMatchingContainers(), "Two chests should produce two results");
		context.assertEquals(near.representativePosition(), result.results().getFirst().representativePosition(), "Nearer chest should sort first");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void doubleChestCountsAllFiftyFourSlotsOnceAndHighlightsBothHalves(TestContext context) {
		LogicalContainer chest = doubleChest(context, BASE);
		chest.inventory().setStack(0, new ItemStack(Items.IRON_INGOT, 32));
		chest.inventory().setStack(53, new ItemStack(Items.IRON_INGOT, 32));
		SessionFixture fixture = openSession(context, chest);
		WorkshopItemSearchResultPayload result = search(fixture, Items.IRON_INGOT, new WorkshopItemSearchService(fixture.manager()), 3);
		context.assertEquals(64L, result.totalItemCount(), "Both double chest halves should be counted");
		context.assertEquals(1, result.totalMatchingContainers(), "Double chest should produce one logical result");
		context.assertEquals(2, result.results().getFirst().highlightPositions().size(), "Double chest should highlight both halves");
		context.assertEquals(2, result.results().getFirst().matchingSlotCount(), "Both matching slots should be reported");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void barrelAndTrappedChestAreSearchable(TestContext context) {
		LogicalContainer barrel = place(context, BASE, Blocks.BARREL.getDefaultState().with(BarrelBlock.FACING, Direction.UP));
		LogicalContainer trapped = place(context, BASE.add(3, 0, 0), Blocks.TRAPPED_CHEST.getDefaultState());
		barrel.inventory().setStack(0, new ItemStack(Items.IRON_INGOT, 5));
		trapped.inventory().setStack(0, new ItemStack(Items.IRON_INGOT, 7));
		SessionFixture fixture = openSession(context, barrel);
		WorkshopItemSearchResultPayload result = search(fixture, Items.IRON_INGOT, new WorkshopItemSearchService(fixture.manager()), 4);
		context.assertEquals(12L, result.totalItemCount(), "Barrel and trapped chest counts should be combined");
		context.assertEquals(2, result.totalMatchingContainers(), "Barrel and trapped chest should both be returned");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void exactTagAndWhitelistLabelsDoNotHideExistingItems(TestContext context) {
		LogicalContainer exact = place(context, BASE, Blocks.CHEST.getDefaultState());
		LogicalContainer tag = place(context, BASE.add(3, 0, 0), Blocks.CHEST.getDefaultState());
		LogicalContainer whitelist = place(context, BASE.add(6, 0, 0), Blocks.CHEST.getDefaultState());
		ContainerLabelService.applyAtomically(exact, ContainerLabelRule.exact(Items.IRON_INGOT));
		ContainerLabelService.applyAtomically(tag, ContainerLabelRule.itemTag(ItemTags.LOGS.id()));
		ContainerLabelService.applyAtomically(whitelist, ContainerLabelRule.whitelist(List.of(
			ContainerLabelEntry.item(Identifier.ofVanilla("iron_ingot")),
			ContainerLabelEntry.itemTag(ItemTags.LOGS.id())
		)));
		exact.inventory().setStack(0, new ItemStack(Items.IRON_INGOT, 1));
		tag.inventory().setStack(0, new ItemStack(Items.IRON_INGOT, 2));
		whitelist.inventory().setStack(0, new ItemStack(Items.IRON_INGOT, 3));
		SessionFixture fixture = openSession(context, exact);
		WorkshopItemSearchResultPayload result = search(fixture, Items.IRON_INGOT, new WorkshopItemSearchService(fixture.manager()), 5);
		context.assertEquals(6L, result.totalItemCount(), "Labels must not change search counting");
		context.assertEquals(3, result.totalMatchingContainers(), "All labeled containers should remain searchable");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void potionVariantsCountByItemAndReportMultipleVariants(TestContext context) {
		LogicalContainer barrel = place(context, BASE, Blocks.BARREL.getDefaultState());
		barrel.inventory().setStack(0, PotionContentsComponent.createStack(Items.POTION, Potions.WATER));
		barrel.inventory().setStack(1, PotionContentsComponent.createStack(Items.POTION, Potions.HEALING));
		SessionFixture fixture = openSession(context, barrel);
		WorkshopItemSearchResultPayload result = search(fixture, Items.POTION, new WorkshopItemSearchService(fixture.manager()), 6);
		context.assertEquals(2L, result.totalItemCount(), "Different potion effects should count as the same item type");
		context.assertTrue(result.results().getFirst().multipleVariants(), "Different potion components should report multiple variants");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void differentItemsAreNotCounted(TestContext context) {
		LogicalContainer chest = place(context, BASE, Blocks.CHEST.getDefaultState());
		chest.inventory().setStack(0, new ItemStack(Items.GOLD_INGOT, 16));
		SessionFixture fixture = openSession(context, chest);
		WorkshopItemSearchResultPayload result = search(fixture, Items.IRON_INGOT, new WorkshopItemSearchService(fixture.manager()), 7);
		context.assertEquals(WorkshopItemSearchResultCode.NOT_FOUND, result.resultId(), "Different items should not match");
		context.assertEquals(16, chest.inventory().count(Items.GOLD_INGOT), "Nonmatching stacks must remain untouched");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void lockedChestDoesNotLeakItsContents(TestContext context) {
		LogicalContainer opened = place(context, BASE, Blocks.CHEST.getDefaultState());
		LogicalContainer locked = place(context, BASE.add(3, 0, 0), Blocks.CHEST.getDefaultState());
		ChestBlockEntity blockEntity = (ChestBlockEntity)context.getWorld().getBlockEntity(locked.representativePosition());
		ItemStack lockedChestItem = new ItemStack(Items.CHEST);
		lockedChestItem.set(DataComponentTypes.LOCK, new ContainerLock("secret"));
		blockEntity.readComponents(lockedChestItem);
		locked.inventory().setStack(0, new ItemStack(Items.NETHERITE_SCRAP, 64));
		SessionFixture fixture = openSession(context, opened);
		context.assertTrue(
			!((SilentContainerAccess)blockEntity).workshopZone$canOpenSilently(fixture.player()),
			"Locked chest must reject a player without the named key"
		);
		WorkshopItemSearchResultPayload result = search(fixture, Items.NETHERITE_SCRAP, new WorkshopItemSearchService(fixture.manager()), 8);
		context.assertEquals(WorkshopItemSearchResultCode.NOT_FOUND, result.resultId(), "Locked chest contents must not be disclosed");
		context.assertEquals(0L, result.totalItemCount(), "Locked chest quantity must remain private");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void ungeneratedLootContainerIsSkippedWithoutGeneratingLoot(TestContext context) {
		LogicalContainer opened = place(context, BASE, Blocks.CHEST.getDefaultState());
		LogicalContainer lootTarget = place(context, BASE.add(3, 0, 0), Blocks.CHEST.getDefaultState());
		ChestBlockEntity blockEntity = (ChestBlockEntity)context.getWorld().getBlockEntity(lootTarget.representativePosition());
		RegistryKey<net.minecraft.loot.LootTable> lootTable = RegistryKey.of(
			RegistryKeys.LOOT_TABLE, Identifier.of("workshop_zone_gametest", "search_must_not_generate")
		);
		blockEntity.setLootTable(lootTable);
		SessionFixture fixture = openSession(context, opened);
		WorkshopItemSearchResultPayload result = search(
			fixture, Items.IRON_INGOT, new WorkshopItemSearchService(fixture.manager()), 81
		);
		context.assertEquals(WorkshopItemSearchResultCode.NOT_FOUND, result.resultId(), "Pending loot target should be skipped");
		context.assertEquals(lootTable, blockEntity.getLootTable(), "Search must not generate or clear a pending loot table");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void searchCallbackDenialDoesNotLeakContents(TestContext context) {
		LogicalContainer chest = place(context, BASE, Blocks.CHEST.getDefaultState());
		chest.inventory().setStack(0, new ItemStack(Items.IRON_INGOT, 64));
		SessionFixture fixture = openSession(context, chest);
		WorkshopContainerAccessService access = new WorkshopContainerAccessService(
			(player, world, entry, state) -> true,
			(player, world, container, targetItem) -> false
		);
		WorkshopItemSearchResultPayload result = search(fixture, Items.IRON_INGOT, new WorkshopItemSearchService(fixture.manager(), access), 9);
		context.assertEquals(WorkshopItemSearchResultCode.NO_ACCESSIBLE_CONTAINERS, result.resultId(), "Denied container should be skipped without disclosure");
		context.assertEquals(0L, result.totalItemCount(), "Denied quantity must remain private");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void containerBeyondEightBlocksFromPlayerIsSkipped(TestContext context) {
		LogicalContainer opened = place(context, BASE, Blocks.CHEST.getDefaultState());
		LogicalContainer far = place(context, BASE.add(7, 0, 0), Blocks.CHEST.getDefaultState());
		far.inventory().setStack(0, new ItemStack(Items.IRON_INGOT, 8));
		SessionFixture fixture = openSession(context, opened);
		BlockPos absoluteBase = context.getAbsolutePos(BASE);
		fixture.player().setPosition(absoluteBase.getX() - 1.5, absoluteBase.getY() + 1, absoluteBase.getZ() + 0.5);
		WorkshopItemSearchResultPayload result = search(fixture, Items.IRON_INGOT, new WorkshopItemSearchService(fixture.manager()), 10);
		context.assertEquals(WorkshopItemSearchResultCode.NOT_FOUND, result.resultId(), "Container beyond eight blocks should be skipped");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void destroyedContainerIsSkippedSafely(TestContext context) {
		LogicalContainer opened = place(context, BASE, Blocks.CHEST.getDefaultState());
		LogicalContainer target = place(context, BASE.add(3, 0, 0), Blocks.CHEST.getDefaultState());
		target.inventory().setStack(0, new ItemStack(Items.IRON_INGOT, 4));
		SessionFixture fixture = openSession(context, opened);
		context.setBlockState(BASE.add(3, 0, 0), Blocks.AIR);
		WorkshopItemSearchResultPayload result = search(fixture, Items.IRON_INGOT, new WorkshopItemSearchService(fixture.manager()), 11);
		context.assertEquals(WorkshopItemSearchResultCode.NOT_FOUND, result.resultId(), "Destroyed target should be skipped without crashing");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void repeatedSearchUsesIndependentTenTickCooldown(TestContext context) {
		LogicalContainer chest = place(context, BASE, Blocks.CHEST.getDefaultState());
		chest.inventory().setStack(0, new ItemStack(Items.IRON_INGOT, 1));
		SessionFixture fixture = openSession(context, chest);
		WorkshopItemSearchService service = new WorkshopItemSearchService(fixture.manager());
		WorkshopItemSearchResultPayload first = search(fixture, Items.IRON_INGOT, service, 12);
		WorkshopItemSearchResultPayload second = search(fixture, Items.IRON_INGOT, service, 13);
		context.assertEquals(WorkshopItemSearchResultCode.SUCCESS, first.resultId(), "First search should run");
		context.assertEquals(WorkshopItemSearchResultCode.COOLDOWN, second.resultId(), "Immediate repeated search should be throttled");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void resultContainerCanStillOpenThroughExistingProtocol(TestContext context) {
		LogicalContainer opened = place(context, BASE, Blocks.CHEST.getDefaultState());
		LogicalContainer target = place(context, BASE.add(3, 0, 0), Blocks.CHEST.getDefaultState());
		target.inventory().setStack(0, new ItemStack(Items.IRON_INGOT, 1));
		SessionFixture fixture = openSession(context, opened);
		WorkshopItemSearchResultPayload result = search(fixture, Items.IRON_INGOT, new WorkshopItemSearchService(fixture.manager()), 14);
		BlockPos targetPosition = result.results().getFirst().representativePosition();
		WorkshopOpenResult openResult = fixture.manager().openTarget(
			fixture.player(), fixture.session().sessionId(), fixture.session().revision(), fixture.session().syncId(), targetPosition
		);
		context.assertEquals(WorkshopOpenResult.SUCCESS, openResult, "Search result should open only through the existing validated protocol");
		finish(fixture, context);
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void twoPlayersDoNotShareSearchCooldownOrResults(TestContext context) {
		LogicalContainer chest = place(context, BASE, Blocks.CHEST.getDefaultState());
		chest.inventory().setStack(0, new ItemStack(Items.IRON_INGOT, 2));
		SessionFixture first = openSession(context, chest);
		SessionFixture second = openSession(context, chest);
		WorkshopItemSearchService service = new WorkshopItemSearchService(first.manager());
		WorkshopItemSearchResultPayload firstResult = search(first, Items.IRON_INGOT, service, 15);
		WorkshopItemSearchResultPayload secondResult = search(second, Items.IRON_INGOT, service, 16);
		context.assertTrue(!first.player().getUuid().equals(second.player().getUuid()), "Mock players should have separate identities");
		context.assertEquals(WorkshopItemSearchResultCode.SUCCESS, firstResult.resultId(), "First player's search should succeed");
		context.assertEquals(WorkshopItemSearchResultCode.SUCCESS, secondResult.resultId(), "Second player's search should have an independent cooldown");
		finish(first, context);
		finish(second, context);
	}

	private static WorkshopItemSearchResultPayload search(
		SessionFixture fixture,
		Item item,
		WorkshopItemSearchService service,
		long requestId
	) {
		return service.search(fixture.player(), new SearchWorkshopItemPayload(
			requestId,
			fixture.session().sessionId(),
			fixture.session().revision(),
			fixture.session().syncId(),
			Registries.ITEM.getId(item)
		));
	}

	private static WorkshopItemCatalogPayload catalog(
		SessionFixture fixture,
		WorkshopItemCatalogService service,
		long requestId
	) {
		return service.catalog(fixture.player(), new RequestWorkshopItemCatalogPayload(
			requestId,
			fixture.session().sessionId(),
			fixture.session().revision(),
			fixture.session().syncId()
		));
	}

	private static SessionFixture openSession(TestContext context, LogicalContainer container) {
		ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
		player.setPosition(
			container.representativePosition().getX() + 0.5,
			container.representativePosition().getY() + 1,
			container.representativePosition().getZ() + 0.5
		);
		var factory = context.getWorld().getBlockState(container.representativePosition())
			.createScreenHandlerFactory(context.getWorld(), container.representativePosition());
		context.assertTrue(factory != null && player.openHandledScreen(factory).isPresent(), "Opened container should have a screen handler");
		WorkshopSessionManager manager = WorkshopSessionManager.getInstance();
		manager.open(player, container.representativePosition(), container.type());
		WorkshopSession session = manager.get(player.getUuid()).orElseThrow();
		return new SessionFixture(player, manager, session);
	}

	private static LogicalContainer place(TestContext context, BlockPos relativePosition, BlockState state) {
		context.setBlockState(relativePosition, state);
		WorkshopContainerResolver.Result result = WorkshopContainerResolver.resolve(
			context.getWorld(), context.getAbsolutePos(relativePosition)
		);
		context.assertTrue(result.successful(), "Placed container should resolve");
		return result.container();
	}

	private static LogicalContainer doubleChest(TestContext context, BlockPos relativePosition) {
		BlockState left = Blocks.CHEST.getDefaultState()
			.with(ChestBlock.FACING, Direction.NORTH)
			.with(ChestBlock.CHEST_TYPE, ChestType.LEFT);
		BlockState right = Blocks.CHEST.getDefaultState()
			.with(ChestBlock.FACING, Direction.NORTH)
			.with(ChestBlock.CHEST_TYPE, ChestType.RIGHT);
		context.setBlockState(relativePosition, left);
		context.setBlockState(relativePosition.add(1, 0, 0), right);
		WorkshopContainerResolver.Result result = WorkshopContainerResolver.resolve(
			context.getWorld(), context.getAbsolutePos(relativePosition)
		);
		context.assertTrue(result.successful(), "Double chest should resolve");
		return result.container();
	}

	private static void finish(SessionFixture fixture, TestContext context) {
		fixture.manager().clear(fixture.player(), false);
		fixture.player().closeHandledScreen();
		context.complete();
	}

	private record SessionFixture(
		ServerPlayerEntity player,
		WorkshopSessionManager manager,
		WorkshopSession session
	) {
	}
}
