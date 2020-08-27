/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.cassandra.db.compaction;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.RateLimiter;
import org.junit.BeforeClass;
import org.junit.After;
import org.junit.Test;

import org.apache.cassandra.config.CFMetaData;
import org.apache.cassandra.db.*;
import org.apache.cassandra.db.lifecycle.LifecycleTransaction;
import org.apache.cassandra.db.rows.EncodingStats;
import org.apache.cassandra.db.lifecycle.SSTableSet;
import org.apache.cassandra.db.rows.UnfilteredRowIterator;
import org.apache.cassandra.dht.ByteOrderedPartitioner.BytesToken;
import org.apache.cassandra.dht.Range;
import org.apache.cassandra.dht.Token;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.cassandra.io.sstable.*;
import org.apache.cassandra.io.sstable.format.SSTableReader;
import org.apache.cassandra.schema.KeyspaceParams;
import org.apache.cassandra.service.ActiveRepairService;
import org.apache.cassandra.SchemaLoader;
import org.apache.cassandra.utils.ByteBufferUtil;
import org.apache.cassandra.utils.concurrent.Refs;
import org.apache.cassandra.UpdateBuilder;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;


public class AntiCompactionTest
{
    private static final String KEYSPACE1 = "AntiCompactionTest";
    private static final String CF = "AntiCompactionTest";
    private static CFMetaData cfm;

    @BeforeClass
    public static void defineSchema() throws ConfigurationException
    {
        SchemaLoader.prepareServer();
        cfm = SchemaLoader.standardCFMD(KEYSPACE1, CF);
        SchemaLoader.createKeyspace(KEYSPACE1,
                                    KeyspaceParams.simple(1),
                                    cfm);
    }

    @After
    public void truncateCF()
    {
        Keyspace keyspace = Keyspace.open(KEYSPACE1);
        ColumnFamilyStore store = keyspace.getColumnFamilyStore(CF);
        store.truncateBlocking();
    }

    @Test
    public void antiCompactOne() throws Exception
    {
        ColumnFamilyStore store = prepareColumnFamilyStore();
        Collection<SSTableReader> sstables = getUnrepairedSSTables(store);
        assertEquals(store.getLiveSSTables().size(), sstables.size());
        Range<Token> range = new Range<Token>(new BytesToken("0".getBytes()), new BytesToken("4".getBytes()));
        List<Range<Token>> ranges = Arrays.asList(range);

        int repairedKeys = 0;
        int nonRepairedKeys = 0;
        try (LifecycleTransaction txn = store.getTracker().tryModify(sstables, OperationType.ANTICOMPACTION);
             Refs<SSTableReader> refs = Refs.ref(sstables))
        {
            if (txn == null)
                throw new IllegalStateException();
            long repairedAt = 1000;
            UUID parentRepairSession = UUID.randomUUID();
            CompactionManager.instance.performAnticompaction(store, ranges, refs, txn, repairedAt, parentRepairSession);
        }

        assertEquals(2, store.getLiveSSTables().size());
        for (SSTableReader sstable : store.getLiveSSTables())
        {
            try (ISSTableScanner scanner = sstable.getScanner((RateLimiter) null))
            {
                while (scanner.hasNext())
                {
                    UnfilteredRowIterator row = scanner.next();
                    if (sstable.isRepaired())
                    {
                        assertTrue(range.contains(row.partitionKey().getToken()));
                        repairedKeys++;
                    }
                    else
                    {
                        assertFalse(range.contains(row.partitionKey().getToken()));
                        nonRepairedKeys++;
                    }
                }
            }
        }
        for (SSTableReader sstable : store.getLiveSSTables())
        {
            assertFalse(sstable.isMarkedCompacted());
            assertEquals(1, sstable.selfRef().globalCount());
        }
        assertEquals(0, store.getTracker().getCompacting().size());
        assertEquals(repairedKeys, 4);
        assertEquals(nonRepairedKeys, 6);
        assertOnDiskState(store, 2);
    }

    @Test
    public void antiCompactionSizeTest() throws InterruptedException, IOException
    {
        Keyspace keyspace = Keyspace.open(KEYSPACE1);
        ColumnFamilyStore cfs = keyspace.getColumnFamilyStore(CF);
        cfs.disableAutoCompaction();
        SSTableReader s = writeFile(cfs, 1000);
        cfs.addSSTable(s);
        Range<Token> range = new Range<Token>(new BytesToken(ByteBufferUtil.bytes(0)), new BytesToken(ByteBufferUtil.bytes(500)));
        Collection<SSTableReader> sstables = cfs.getLiveSSTables();
        UUID parentRepairSession = UUID.randomUUID();
        try (LifecycleTransaction txn = cfs.getTracker().tryModify(sstables, OperationType.ANTICOMPACTION);
             Refs<SSTableReader> refs = Refs.ref(sstables))
        {
            CompactionManager.instance.performAnticompaction(cfs, Arrays.asList(range), refs, txn, 12345, parentRepairSession);
        }
        long sum = 0;
        long rows = 0;
        for (SSTableReader x : cfs.getLiveSSTables())
        {
            sum += x.bytesOnDisk();
            rows += x.getTotalRows();
        }
        assertEquals(sum, cfs.metric.liveDiskSpaceUsed.getCount());
        assertEquals(rows, 1000 * (1000 * 5));//See writeFile for how this number is derived
        assertOnDiskState(cfs, 2);
    }

    private SSTableReader writeFile(ColumnFamilyStore cfs, int count)
    {
        File dir = cfs.getDirectories().getDirectoryForNewSSTables();
        String filename = cfs.getSSTablePath(dir);

        try (SSTableTxnWriter writer = SSTableTxnWriter.create(cfs, filename, 0, 0, new SerializationHeader(true, cfm, cfm.partitionColumns(), EncodingStats.NO_STATS)))
        {
            for (int i = 0; i < count; i++)
            {
                UpdateBuilder builder = UpdateBuilder.create(cfm, ByteBufferUtil.bytes(i));
                for (int j = 0; j < count * 5; j++)
                    builder.newRow("c" + j).add("val", "value1");
                writer.append(builder.build().unfilteredIterator());

            }
            Collection<SSTableReader> sstables = writer.finish(true);
            assertNotNull(sstables);
            assertEquals(1, sstables.size());
            return sstables.iterator().next();
        }
    }

    public void generateSStable(ColumnFamilyStore store, String Suffix)
    {
        for (int i = 0; i < 10; i++)
        {
            String localSuffix = Integer.toString(i);
            new RowUpdateBuilder(cfm, System.currentTimeMillis(), localSuffix + "-" + Suffix)
                    .clustering("c")
                    .add("val", "val" + localSuffix)
                    .build()
                    .applyUnsafe();
        }
        store.forceBlockingFlush();
    }

    @Test
    public void antiCompactTen() throws InterruptedException, IOException
    {
        Keyspace keyspace = Keyspace.open(KEYSPACE1);
        ColumnFamilyStore store = keyspace.getColumnFamilyStore(CF);
        store.disableAutoCompaction();

        for (int table = 0; table < 10; table++)
        {
            generateSStable(store,Integer.toString(table));
        }
        Collection<SSTableReader> sstables = getUnrepairedSSTables(store);
        assertEquals(store.getLiveSSTables().size(), sstables.size());

        Range<Token> range = new Range<Token>(new BytesToken("0".getBytes()), new BytesToken("4".getBytes()));
        List<Range<Token>> ranges = Arrays.asList(range);

        long repairedAt = 1000;
        UUID parentRepairSession = UUID.randomUUID();
        try (LifecycleTransaction txn = store.getTracker().tryModify(sstables, OperationType.ANTICOMPACTION);
             Refs<SSTableReader> refs = Refs.ref(sstables))
        {
            CompactionManager.instance.performAnticompaction(store, ranges, refs, txn, repairedAt, parentRepairSession);
        }
        /*
        Anticompaction will be anti-compacting 10 SSTables but will be doing this two at a time
        so there will be no net change in the number of sstables
         */
        assertEquals(10, store.getLiveSSTables().size());
        int repairedKeys = 0;
        int nonRepairedKeys = 0;
        for (SSTableReader sstable : store.getLiveSSTables())
        {
            try (ISSTableScanner scanner = sstable.getScanner((RateLimiter) null))
            {
                while (scanner.hasNext())
                {
                    try (UnfilteredRowIterator row = scanner.next())
                    {
                        if (sstable.isRepaired())
                        {
                            assertTrue(range.contains(row.partitionKey().getToken()));
                            assertEquals(repairedAt, sstable.getSSTableMetadata().repairedAt);
                            repairedKeys++;
                        }
                        else
                        {
                            assertFalse(range.contains(row.partitionKey().getToken()));
                            assertEquals(ActiveRepairService.UNREPAIRED_SSTABLE, sstable.getSSTableMetadata().repairedAt);
                            nonRepairedKeys++;
                        }
                    }
                }
            }
        }
        assertEquals(repairedKeys, 40);
        assertEquals(nonRepairedKeys, 60);
        assertOnDiskState(store, 10);
    }

    @Test
    public void shouldMutateRepairedAt() throws InterruptedException, IOException
    {
        ColumnFamilyStore store = prepareColumnFamilyStore();
        Collection<SSTableReader> sstables = getUnrepairedSSTables(store);
        assertEquals(store.getLiveSSTables().size(), sstables.size());
        Range<Token> range = new Range<Token>(new BytesToken("/".getBytes()), new BytesToken("9999".getBytes()));
        List<Range<Token>> ranges = Arrays.asList(range);
        UUID parentRepairSession = UUID.randomUUID();

        try (LifecycleTransaction txn = store.getTracker().tryModify(sstables, OperationType.ANTICOMPACTION);
             Refs<SSTableReader> refs = Refs.ref(sstables))
        {
            CompactionManager.instance.performAnticompaction(store, ranges, refs, txn, 1, parentRepairSession);
        }

        SSTableReader sstable = Iterables.get(store.getLiveSSTables(), 0);
        assertThat(store.getLiveSSTables().size(), is(1));
        assertThat(sstable.isRepaired(), is(true));
        assertThat(sstable.selfRef().globalCount(), is(1));
        assertThat(store.getTracker().getCompacting().size(), is(0));
        assertOnDiskState(store, 1);
    }

    @Test
    public void shouldAntiCompactSSTable() throws IOException, InterruptedException, ExecutionException
    {
        ColumnFamilyStore store = prepareColumnFamilyStore();
        Collection<SSTableReader> sstables = getUnrepairedSSTables(store);
        assertEquals(store.getLiveSSTables().size(), sstables.size());
        // SSTable range is 0 - 10, repair just a subset of the ranges (0 - 4) of the SSTable. Should result in
        // one repaired and one unrepaired SSTable
        Range<Token> range = new Range<Token>(new BytesToken("/".getBytes()), new BytesToken("4".getBytes()));
        List<Range<Token>> ranges = Arrays.asList(range);
        UUID parentRepairSession = UUID.randomUUID();

        try (LifecycleTransaction txn = store.getTracker().tryModify(sstables, OperationType.ANTICOMPACTION);
             Refs<SSTableReader> refs = Refs.ref(sstables))
        {
            CompactionManager.instance.performAnticompaction(store, ranges, refs, txn, 1, parentRepairSession);
        }

        SortedSet<SSTableReader> sstablesSorted = new TreeSet<>(SSTableReader.generationReverseComparator.reversed());
        sstablesSorted.addAll(store.getLiveSSTables());

        SSTableReader sstable = sstablesSorted.first();
        assertThat(store.getLiveSSTables().size(), is(2));
        assertThat(sstable.isRepaired(), is(true));
        assertThat(sstable.selfRef().globalCount(), is(1));
        assertThat(store.getTracker().getCompacting().size(), is(0));

        // Test we don't anti-compact already repaired SSTables. repairedAt shouldn't change for the already repaired SSTable (first)
        sstables = store.getLiveSSTables();
        // Range that's a subset of the repaired SSTable's ranges, so would cause an anti-compaction (if it wasn't repaired)
        range = new Range<Token>(new BytesToken("/".getBytes()), new BytesToken("2".getBytes()));
        ranges = Arrays.asList(range);
        try (Refs<SSTableReader> refs = Refs.ref(sstables))
        {
            // use different repairedAt to ensure it doesn't change
            ListenableFuture fut = CompactionManager.instance.submitAntiCompaction(store, ranges, refs, 200, parentRepairSession);
            fut.get();
        }

        sstablesSorted.clear();
        sstablesSorted.addAll(store.getLiveSSTables());
        assertThat(sstablesSorted.size(), is(2));
        assertThat(sstablesSorted.first().isRepaired(), is(true));
        assertThat(sstablesSorted.last().isRepaired(), is(false));
        assertThat(sstablesSorted.first().getSSTableMetadata().repairedAt, is(1L));
        assertThat(sstablesSorted.last().getSSTableMetadata().repairedAt, is(0L));
        assertThat(sstablesSorted.first().selfRef().globalCount(), is(1));
        assertThat(sstablesSorted.last().selfRef().globalCount(), is(1));
        assertThat(store.getTracker().getCompacting().size(), is(0));

        // Test repairing all the ranges of the repaired SSTable. Should mutate repairedAt without anticompacting,
        // but leave the unrepaired SSTable as is.
        range = new Range<Token>(new BytesToken("/".getBytes()), new BytesToken("4".getBytes()));
        ranges = Arrays.asList(range);

        try (Refs<SSTableReader> refs = Refs.ref(sstables))
        {
            // Same repaired at, but should be changed on the repaired SSTable now
            ListenableFuture fut = CompactionManager.instance.submitAntiCompaction(store, ranges, refs, 200, parentRepairSession);
            fut.get();
        }

        sstablesSorted.clear();
        sstablesSorted.addAll(store.getLiveSSTables());

        assertThat(sstablesSorted.size(), is(2));
        assertThat(sstablesSorted.first().isRepaired(), is(true));
        assertThat(sstablesSorted.last().isRepaired(), is(false));
        assertThat(sstablesSorted.first().getSSTableMetadata().repairedAt, is(200L));
        assertThat(sstablesSorted.last().getSSTableMetadata().repairedAt, is(0L));
        assertThat(sstablesSorted.first().selfRef().globalCount(), is(1));
        assertThat(sstablesSorted.last().selfRef().globalCount(), is(1));
        assertThat(store.getTracker().getCompacting().size(), is(0));

        // Repair whole range. Should mutate repairedAt on repaired SSTable (again) and
        // mark unrepaired SSTable as repaired
        range = new Range<Token>(new BytesToken("/".getBytes()), new BytesToken("999".getBytes()));
        ranges = Arrays.asList(range);

        try (Refs<SSTableReader> refs = Refs.ref(sstables))
        {
            // Both SSTables should have repairedAt of 400
            ListenableFuture fut = CompactionManager.instance.submitAntiCompaction(store, ranges, refs, 400, parentRepairSession);
            fut.get();
        }

        sstablesSorted.clear();
        sstablesSorted.addAll(store.getLiveSSTables());

        assertThat(sstablesSorted.size(), is(2));
        assertThat(sstablesSorted.first().isRepaired(), is(true));
        assertThat(sstablesSorted.last().isRepaired(), is(true));
        assertThat(sstablesSorted.first().getSSTableMetadata().repairedAt, is(400L));
        assertThat(sstablesSorted.last().getSSTableMetadata().repairedAt, is(400L));
        assertThat(sstablesSorted.first().selfRef().globalCount(), is(1));
        assertThat(sstablesSorted.last().selfRef().globalCount(), is(1));
        assertThat(store.getTracker().getCompacting().size(), is(0));
        assertOnDiskState(store, 2);
    }


    @Test
    public void shouldSkipAntiCompactionForNonIntersectingRange() throws InterruptedException, IOException
    {
        Keyspace keyspace = Keyspace.open(KEYSPACE1);
        ColumnFamilyStore store = keyspace.getColumnFamilyStore(CF);
        store.disableAutoCompaction();

        for (int table = 0; table < 10; table++)
        {
            generateSStable(store,Integer.toString(table));
        }
        Collection<SSTableReader> sstables = getUnrepairedSSTables(store);
        assertEquals(store.getLiveSSTables().size(), sstables.size());

        Range<Token> range = new Range<Token>(new BytesToken("-1".getBytes()), new BytesToken("-10".getBytes()));
        List<Range<Token>> ranges = Arrays.asList(range);
        UUID parentRepairSession = UUID.randomUUID();

        try (LifecycleTransaction txn = store.getTracker().tryModify(sstables, OperationType.ANTICOMPACTION);
             Refs<SSTableReader> refs = Refs.ref(sstables))
        {
            CompactionManager.instance.performAnticompaction(store, ranges, refs, txn, 1, parentRepairSession);
        }

        assertThat(store.getLiveSSTables().size(), is(10));
        assertThat(Iterables.get(store.getLiveSSTables(), 0).isRepaired(), is(false));
        assertOnDiskState(store, 10);
    }

    private ColumnFamilyStore prepareColumnFamilyStore()
    {
        Keyspace keyspace = Keyspace.open(KEYSPACE1);
        ColumnFamilyStore store = keyspace.getColumnFamilyStore(CF);
        store.disableAutoCompaction();
        for (int i = 0; i < 10; i++)
        {
            new RowUpdateBuilder(cfm, System.currentTimeMillis(), Integer.toString(i))
                .clustering("c")
                .add("val", "val")
                .build()
                .applyUnsafe();
        }
        store.forceBlockingFlush();
        return store;
    }

    @After
    public void truncateCfs()
    {
        Keyspace keyspace = Keyspace.open(KEYSPACE1);
        ColumnFamilyStore store = keyspace.getColumnFamilyStore(CF);
        store.truncateBlocking();
    }

    private static Set<SSTableReader> getUnrepairedSSTables(ColumnFamilyStore cfs)
    {
        return ImmutableSet.copyOf(cfs.getTracker().getView().sstables(SSTableSet.LIVE, (s) -> !s.isRepaired()));
    }

    static void assertOnDiskState(ColumnFamilyStore cfs, int expectedSSTableCount)
    {
        LifecycleTransaction.waitForDeletions();
        assertEquals(expectedSSTableCount, cfs.getLiveSSTables().size());
        Set<Integer> liveGenerations = cfs.getLiveSSTables().stream().map(sstable -> sstable.descriptor.generation).collect(Collectors.toSet());
        int fileCount = 0;
        for (File f : cfs.getDirectories().getCFDirectories())
        {
            for (File sst : f.listFiles())
            {
                if (sst.getName().contains("Data"))
                {
                    Descriptor d = Descriptor.fromFilename(sst.getAbsolutePath());
                    assertTrue(liveGenerations.contains(d.generation));
                    fileCount++;
                }
            }
        }
        assertEquals(expectedSSTableCount, fileCount);
    }

}
