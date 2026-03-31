package com.oracle.pic.networking.lvv.service.kiev;

import com.google.api.client.util.Lists;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.oracle.pic.commons.exceptions.server.ErrorCode;
import com.oracle.pic.commons.exceptions.server.RenderableException;
import com.oracle.pic.commons.metrics.MetricsScope;
import com.oracle.pic.kiev.Transaction;
import com.oracle.pic.kiev.mapping.Index;
import com.oracle.pic.kiev.mapping.MappedHashBucket;
import com.oracle.pic.kiev.mapping.ScanPage;
import com.oracle.pic.kiev.mapping.token.PaginationTokenSerializer;
import com.oracle.pic.networking.lvv.service.dependencies.metrics.MetricNames;
import java.util.List;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/** Provides Kiev functions to help with managing the data stored in Kiev DB */
@Slf4j
@Singleton
@ToString
public class BlockDetailsDao {
    private static final int DEFAULT_PAGE_SIZE = 1000;
    private static final int MAX_WRITES_PER_TRANSACTION = 50;
    private final ConfigurationStore<BlockDetails.Block, BlockDetails> blockDetailsStore;
    private final MappedHashBucket<BlockDetails.Block, BlockDetails> blockDetailsProvider;
    private PaginationTokenSerializer serializer;

    private final Index<BlockDetails.ProjectIdIndex, BlockDetails> projectIdIndex;

    @Inject
    public BlockDetailsDao(
            @NonNull ConfigurationStore<BlockDetails.Block, BlockDetails> blockDetailsStore,
            @NonNull PaginationTokenSerializer serializer,
            @NonNull MappedHashBucket<BlockDetails.Block, BlockDetails> blockDetailsProvider) {
        this.blockDetailsStore = blockDetailsStore;
        this.serializer = serializer;
        this.blockDetailsProvider = blockDetailsProvider;
        this.projectIdIndex =
                blockDetailsProvider.getIndex(
                        BlockDetails.PROJECT_ID_COLUMN_NAME, BlockDetails.ProjectIdIndex.class);
    }

    public void addBlockDetails(@NonNull List<BlockDetails> blocks, Transaction txn) {
        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.BLOCK_DETAILS.name())) {

            log.info("Adding the following block details to database: {}", blocks);

            scope.emit(MetricNames.BlockDetails.AddBlockDetails.name(), 1.0);

            if (blocks.size() > MAX_WRITES_PER_TRANSACTION) {
                log.error("Cannot assign more than {} blocks at once", MAX_WRITES_PER_TRANSACTION);
                scope.emit(MetricNames.BlockDetails.BlockSizeExceedsMaximum.name(), 1.0);
                throw new RenderableException(
                        ErrorCode.InvalidParameter,
                        "A project cannot have more than {} blocks associated with it",
                        MAX_WRITES_PER_TRANSACTION);
            }

            blocks.forEach(block -> blockDetailsStore.createItem(txn, block));

            scope.recordSuccess();
        }
    }

    public void deleteBlockDetails(@NonNull String projectId, Transaction txn) {
        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.BLOCK_DETAILS.name())) {
            scope.emit(MetricNames.BlockDetails.DeleteBlockDetails.name(), 1.0);

            log.info("Deleting Blocks associated with project {}", projectId);

            List<BlockDetails> blocks = getBlockDetailsForProject(projectId);

            log.info("Deleting the following blocks {}", blocks);

            // In the future, if we see cases of more than MAX_WRITES_PER_TRANSACTION blocks being
            // deleted at once, we should have a batch delete method
            if (blocks.size() > MAX_WRITES_PER_TRANSACTION) {
                log.error("Cannot delete more than {} blocks at once", MAX_WRITES_PER_TRANSACTION);
                scope.emit(MetricNames.BlockDetails.BlockSizeExceedsMaximum.name(), 1.0);
                throw new RenderableException(
                        ErrorCode.IncorrectState,
                        "A project should not be having more than {} blocks associated with it",
                        MAX_WRITES_PER_TRANSACTION);
            }

            if (blocks.isEmpty()) {
                log.error("There should be at least one block associated with a project");
                scope.emit(MetricNames.BlockDetails.NoBlocksInProject.name(), 1.0);
                throw new RenderableException(
                        ErrorCode.IncorrectState,
                        "There should be at least one block associated with a project");
            }

            blocks.forEach(blockItem -> blockDetailsStore.deleteItem(txn, blockItem.getBlock()));
            scope.recordSuccess();
        }
    }

    public BlockDetails getBlockDetails(@NonNull String building, @NonNull String blockNumber) {
        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.BLOCK_DETAILS.name())) {
            scope.emit(MetricNames.BlockDetails.GetBlockDetails.name(), 1.0);

            BlockDetails.Block block =
                    BlockDetails.Block.builder()
                            .blockNumber(blockNumber)
                            .building(building)
                            .build();

            scope.recordSuccess();
            return blockDetailsStore.getItem(block);
        } catch (RuntimeException e) {
            log.info("Block {} in building {} does not exist", blockNumber, building);
            return null;
        }
    }

    public List<BlockDetails> getBlockDetailsForProject(@NonNull String projectId) {
        try (MetricsScope scope =
                MetricsScope.create(MetricNames.MetricScopeNames.BLOCK_DETAILS.name())) {

            log.info("Getting block details for project {}", projectId);

            scope.emit(MetricNames.BlockDetails.GetBlockDetailsForProject.name(), 1.0);

            BlockDetails.ProjectIdIndex prefix =
                    BlockDetails.ProjectIdIndex.builder().projectId(projectId).build();

            List<BlockDetails> result = Lists.newArrayList();

            Preconditions.checkNotNull(projectIdIndex, "projectId is null");
            ScanPage<BlockDetails> page = projectIdIndex.beginPrefixScan(prefix, DEFAULT_PAGE_SIZE);
            while (page != null) {
                KievRateLimiter.throttle();
                List<BlockDetails> pageResults = page.results();
                if (pageResults != null) {
                    List<BlockDetails> filteredPage =
                            pageResults.stream()
                                    .filter(blk -> blk != null)
                                    .filter(blk -> blk.getProjectId().equals(projectId))
                                    .collect(Collectors.toList());

                    result.addAll(filteredPage);
                }

                // Setup next page
                if (page.hasNext()) {
                    page = projectIdIndex.scan(page.paginationToken());
                } else {
                    page = null;
                }
            }
            scope.recordSuccess();

            log.info("Found {} block details for project {}: {}", result.size(), projectId, result);

            return result;
        }
    }
}
