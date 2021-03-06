package com.maxdemarzi;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.maxdemarzi.results.*;
import de.siegmar.fastcsv.writer.CsvAppender;
import de.siegmar.fastcsv.writer.CsvWriter;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.*;
import org.neo4j.helpers.collection.Pair;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;
import org.roaringbitmap.IntIterator;
import org.roaringbitmap.RoaringBitmap;
import org.roaringbitmap.longlong.Roaring64NavigableMap;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;


public class Procedures {

    // This field declares that we need a GraphDatabaseService
    // as context when any procedure in this class is invoked
    @Context
    public GraphDatabaseService db;

    // This gives us a log instance that outputs messages to the
    // standard log, normally found under `data/log/neo4j.log`
    @Context
    public Log log;

    @Procedure(name = "com.maxdemarzi.echo", mode = Mode.READ)
    @Description("CALL com.maxdemarzi.echo(String said)")
    public Stream<StringResult> echo(@Name("said") String said) {
        return Stream.of(new StringResult(said));
    }

    @Procedure(name = "com.maxdemarzi.network.count", mode = Mode.READ)
    @Description("CALL com.maxdemarzi.network.count(String username, Long distance)")
    public Stream<LongResult> networkCount(@Name("username") String username, @Name(value = "distance", defaultValue = "1") Long distance) {
        if (distance < 1) return Stream.empty();

        Node user = db.findNode(Label.label("User"), "username", username);
        if (user == null) {
            return Stream.empty();
        } else {
            Iterator<Node> iterator;
            Node current;

            HashSet<Node> seen = new HashSet<>();
            HashSet<Node> nextA = new HashSet<>();
            HashSet<Node> nextB = new HashSet<>();

            seen.add(user);

            // First Hop
            for (Relationship r : user.getRelationships()) {
                nextB.add(r.getOtherNode(user));
            }

            for (int i = 1; i < distance; i++) {
                // next even Hop
                nextB.removeAll(seen);
                seen.addAll(nextB);
                nextA.clear();
                iterator = nextB.iterator();
                while (iterator.hasNext()) {
                    current = iterator.next();
                    for (Relationship r : current.getRelationships()) {
                        nextA.add(r.getOtherNode(current));
                    }
                }

                i++;
                if (i < distance) {
                    // next odd Hop
                    nextA.removeAll(seen);
                    seen.addAll(nextA);
                    nextB.clear();
                    iterator = nextA.iterator();
                    while (iterator.hasNext()) {
                        current = iterator.next();
                        for (Relationship r : current.getRelationships()) {
                            nextB.add(r.getOtherNode(current));
                        }
                    }
                }
            }

            if ((distance % 2) == 0) {
                seen.addAll(nextA);
            } else {
                seen.addAll(nextB);
            }

            // remove starting node
            seen.remove(user);

            return Stream.of(new LongResult((long) seen.size()));
        }
    }

    @Procedure(name= "com.maxdemarzi.network.count2", mode = Mode.READ)
    @Description("com.maxdemarzi.network.count2(String username, Long distance)")
    public Stream<LongResult> networkCount2(@Name("username") String username, @Name(value = "distance", defaultValue = "1") Long distance) {
        if (distance < 1) return Stream.empty();

        Node user = db.findNode(Label.label("User"), "username", username);
        if (user == null) {
            return Stream.empty();
        } else {
            HashSet<Node> nodes = new HashSet<>();
            TraversalDescription td = db.traversalDescription()
                    .depthFirst()
                    .expand(PathExpanders.allTypesAndDirections())
                    .evaluator(Evaluators.toDepth(distance.intValue()))
                    .uniqueness(Uniqueness.RELATIONSHIP_PATH);
            for (Path ignored : td.traverse(user)) {
                nodes.add(ignored.endNode());
            }

            // remove starting node
            nodes.remove(user);
            return Stream.of(new LongResult((long) nodes.size()));
        }
    }

    @Procedure(name ="com.maxdemarzi.network.count3", mode = Mode.READ)
    @Description("com.maxdemarzi.network.count3(String username, Long distance)")
    public Stream<LongResult> networkCount3(@Name("username") String username, @Name(value = "distance", defaultValue = "1") Long distance) {
        if (distance < 1) return Stream.empty();

        Node user = db.findNode(Label.label("User"), "username", username);
        if (user == null) {
            return Stream.empty();
        } else {
            TraversalDescription td = db.traversalDescription()
                    .breadthFirst()
                    .expand(PathExpanders.allTypesAndDirections())
                    .evaluator(Evaluators.toDepth(distance.intValue()))
                    .uniqueness(Uniqueness.NODE_GLOBAL);
            int count = 0;
            for (Path ignored : td.traverse(user)) {
                count++;
            }

            // remove starting node
            count--;
            return Stream.of(new LongResult((long) count));
        }
    }

    @Procedure(name = "com.maxdemarzi.network.count4", mode = Mode.READ)
    @Description("com.maxdemarzi.network.count4(String username, Long distance)")
    public Stream<LongResult> networkCount4(@Name("username") String username, @Name(value = "distance", defaultValue = "1") Long distance) {
        if (distance < 1) return Stream.empty();

        Node user = db.findNode(Label.label("User"), "username", username);
        if (user == null) {
            return Stream.empty();
        } else {
            Iterator<Long> iterator;
            Node current;
            Long currentId;
            HashSet<Long> seen = new HashSet<>();
            HashSet<Long> nextA = new HashSet<>();
            HashSet<Long> nextB = new HashSet<>();

            seen.add(user.getId());

            // First Hop
            for (Relationship r : user.getRelationships()) {
                nextB.add(r.getOtherNodeId(user.getId()));
            }

            for (int i = 1; i < distance; i++) {
                // next even Hop
                nextB.removeAll(seen);
                seen.addAll(nextB);
                nextA.clear();
                iterator = nextB.iterator();
                while (iterator.hasNext()) {
                    currentId = iterator.next();
                    current = db.getNodeById(currentId);
                    for (Relationship r : current.getRelationships()) {
                        nextA.add(r.getOtherNodeId(currentId));
                    }
                }

                i++;
                if (i < distance) {
                    // next odd Hop
                    nextA.removeAll(seen);
                    seen.addAll(nextA);
                    nextB.clear();
                    iterator = nextA.iterator();
                    while (iterator.hasNext()) {
                        currentId = iterator.next();
                        current = db.getNodeById(currentId);
                        for (Relationship r : current.getRelationships()) {
                            nextB.add(r.getOtherNodeId(currentId));
                        }
                    }
                }
            }

            if ((distance % 2) == 0) {
                seen.addAll(nextA);
            } else {
                seen.addAll(nextB);
            }

            // remove starting node
            seen.remove(user.getId());

            return Stream.of(new LongResult((long) seen.size()));
        }
    }

    @Procedure(name = "com.maxdemarzi.network.count5", mode = Mode.READ)
    @Description("com.maxdemarzi.network.count5(String username, Long distance)")
    public Stream<LongResult> networkCount5(@Name("username") String username, @Name(value = "distance", defaultValue = "1") Long distance) {
        if (distance < 1) return Stream.empty();

        Node user = db.findNode(Label.label("User"), "username", username);
        if (user == null) {
            return Stream.empty();
        } else {

            Node node;
            // Initialize bitmaps for iteration
            Roaring64NavigableMap seen = new Roaring64NavigableMap();
            Roaring64NavigableMap nextA = new Roaring64NavigableMap();
            Roaring64NavigableMap nextB = new Roaring64NavigableMap();
            long nodeId = user.getId();
            seen.add(nodeId);
            Iterator<Long> iterator;

            // First Hop
            for (Relationship r : user.getRelationships()) {
                nextB.add(r.getOtherNodeId(nodeId));
            }


            for (int i = 1; i < distance; i++) {
                // next even Hop
                nextB.andNot(seen);
                seen.or(nextB);
                nextA.clear();
                iterator = nextB.iterator();
                while (iterator.hasNext()) {
                    nodeId = iterator.next();
                    node = db.getNodeById(nodeId);
                    for (Relationship r : node.getRelationships()) {
                        nextA.add(r.getOtherNodeId(nodeId));
                    }
                }

                i++;
                if (i < distance) {
                    // next odd Hop
                    nextA.andNot(seen);
                    seen.or(nextA);
                    nextB.clear();
                    iterator = nextA.iterator();
                    while (iterator.hasNext()) {
                        nodeId = iterator.next();
                        node = db.getNodeById(nodeId);
                        for (Relationship r : node.getRelationships()) {
                            nextB.add(r.getOtherNodeId(nodeId));
                        }
                    }
                }
            }

            if ((distance % 2) == 0) {
                seen.or(nextA);
            } else {
                seen.or(nextB);
            }
            // remove starting node
            seen.removeLong(user.getId());

            return Stream.of(new LongResult(seen.getLongCardinality()));
        }
    }

    private static final Comparator<WeightedPathResult> comparator = Comparator.comparingInt(WeightedPathResult::getLength)
            .thenComparing(Comparator.comparingDouble(WeightedPathResult::getWeight).reversed());


    @Procedure(name = "com.maxdemarzi.reach.after", mode = Mode.READ)
    @Description("com.maxdemarzi.reach.after(String username1, String username2)")
    public Stream<WeightedPathResult> reachAfter(@Name("username1") String username1, @Name("username2") String username2) {
        Node user1 = db.findNode(Label.label("User"), "username", username1);
        Node user2 = db.findNode(Label.label("User"), "username", username2);
        if (user1 == null || user2 == null) {
            return Stream.empty();
        }

        TraversalDescription leftSide = db.traversalDescription()
                .depthFirst()
                .expand(PathExpanders.allTypesAndDirections())
                .evaluator(Evaluators.toDepth(2))
                .uniqueness(Uniqueness.NODE_PATH);

        TraversalDescription rightSide = db.traversalDescription()
                .depthFirst()
                .expand(PathExpanders.allTypesAndDirections())
                .evaluator(Evaluators.toDepth(2))
                .uniqueness(Uniqueness.NODE_PATH);

        BidirectionalTraversalDescription bidirtd = db.bidirectionalTraversalDescription()
                .startSide(leftSide)
                .endSide(rightSide);

        ArrayList<WeightedPathResult> list = new ArrayList<>();

        outerloop:
        for (Path path : bidirtd.traverse(user1, user2)) {
            double weight = 0.0;
            for (Relationship r : path.relationships()) {
                double each = (double) r.getProperty("weight");
                if (each >= 0.80) {
                    weight += each;
                } else {
                    continue outerloop;
                }
            }
            list.add(new WeightedPathResult(path, weight / path.length()));
        }


        list.sort(comparator);

        return list.stream().limit(100);
    }

    private static final GoodFriendEvaluator evaluator = new GoodFriendEvaluator();
    private static final GoodFriendExpander expander = new GoodFriendExpander();

    @Procedure(name = "com.maxdemarzi.reach.evaluator", mode = Mode.READ)
    @Description("com.maxdemarzi.reach.evaluator(String username1, String username2)")
    public Stream<WeightedPathResult> reachEvaluator(@Name("username1") String username1, @Name("username2") String username2) {
        Node user1 = db.findNode(Label.label("User"), "username", username1);
        Node user2 = db.findNode(Label.label("User"), "username", username2);
        if (user1 == null || user2 == null) {
            return Stream.empty();
        }

        TraversalDescription eachSide = db.traversalDescription()
                .breadthFirst()
                .expand(PathExpanders.allTypesAndDirections())
                .evaluator(evaluator)
                .evaluator(Evaluators.toDepth(2))
                .uniqueness(Uniqueness.NODE_PATH);

        BidirectionalTraversalDescription bidirtd = db.bidirectionalTraversalDescription()
                .mirroredSides(eachSide)
                .collisionEvaluator(evaluator);

        ArrayList<WeightedPathResult> list = new ArrayList<>();

        for (Path path : bidirtd.traverse(user1, user2)) {
            double weight = 0.0;
            for (Relationship r : path.relationships()) {
                weight += (double) r.getProperty("weight");
            }
            list.add(new WeightedPathResult(path, weight / path.length()));
        }

        list.sort(comparator);

        return list.stream().limit(100);
    }

    @Procedure(name = "com.maxdemarzi.reach.expander", mode = Mode.READ)
    @Description("com.maxdemarzi.reach.expander(String username1, String username2)")
    public Stream<WeightedPathResult> reachExpander(@Name("username1") String username1, @Name("username2") String username2) {
        Node user1 = db.findNode(Label.label("User"), "username", username1);
        Node user2 = db.findNode(Label.label("User"), "username", username2);
        if (user1 == null || user2 == null) {
            return Stream.empty();
        }

        TraversalDescription eachSide = db.traversalDescription()
                .breadthFirst()
                .expand(expander)
                .evaluator(Evaluators.toDepth(2))
                .uniqueness(Uniqueness.NODE_PATH);

        BidirectionalTraversalDescription bidirtd = db.bidirectionalTraversalDescription()
                .mirroredSides(eachSide);

        ArrayList<WeightedPathResult> list = new ArrayList<>();

        for (Path path : bidirtd.traverse(user1, user2)) {
            double weight = 0.0;
            for (Relationship r : path.relationships()) {
                weight += (double) r.getProperty("weight");
            }
            list.add(new WeightedPathResult(path, weight / path.length()));
        }

        list.sort(comparator);

        return list.stream().limit(100);
    }

    @Procedure(name = "com.maxdemarzi.reach.both", mode = Mode.READ)
    @Description("com.maxdemarzi.reach.both(String username1, String username2)")
    public Stream<WeightedPathResult> reachBoth(@Name("username1") String username1, @Name("username2") String username2) {
        Node user1 = db.findNode(Label.label("User"), "username", username1);
        Node user2 = db.findNode(Label.label("User"), "username", username2);
        if (user1 == null || user2 == null) {
            return Stream.empty();
        }

        PriorityQueue<WeightedPathResult> paths = new PriorityQueue<>(100, comparator);

        TraversalDescription eachSide = db.traversalDescription()
                .breadthFirst()
                .expand(expander)
                .evaluator(Evaluators.toDepth(2))
                .uniqueness(Uniqueness.NODE_PATH);

        BidirectionalTraversalDescription bidirtd = db.bidirectionalTraversalDescription()
                .mirroredSides(eachSide)
                .collisionEvaluator(new TooManyFriendsEvaluator(paths));

        int count = 0;
        for (Path ignore : bidirtd.traverse(user1, user2)) {
            count++;
        }

        return paths.stream();
    }

    private static GraphDatabaseService graph;

    private static final LoadingCache<Integer, String> usernames = Caffeine.newBuilder()
            .maximumSize(100001L)
            .build(Procedures::getNodeUsername);

    private static final LoadingCache<Integer, RoaringBitmap> nodeFriends = Caffeine.newBuilder()
            .maximumSize(100001L)
            .expireAfterWrite(1L, TimeUnit.HOURS)
            .build(Procedures::getNodeFriends);

    private static String getNodeUsername(Integer key) {
        return (String) graph.getNodeById(key).getProperty("username");
    }

    private static RoaringBitmap getNodeFriends(Integer key) {
        Node user = graph.getNodeById(key);
        RoaringBitmap friends = new RoaringBitmap();
        for (Relationship r : user.getRelationships(RelationshipType.withName("FRIENDS"))) {
            friends.add((int) r.getOtherNodeId(key));
        }
        return friends;
    }

    @Procedure(name = "com.maxdemarzi.fic", mode = Mode.READ)
    @Description("com.maxdemarzi.fic(String path)")
    public Stream<StringResult> friendsInCommon(@Name(value = "path") String path) throws IOException {
        if (graph == null) {
            graph = this.db;
        }
        ResourceIterator userIterator = db.findNodes(Label.label("User"));
        ArrayList<Pair<String, Node>> users = new ArrayList<>();
        while (userIterator.hasNext()) {
            Node user = (Node) userIterator.next();
            String username = usernames.get((int) user.getId());
            users.add(Pair.of(username, user));
        }
        users.sort(Comparator.comparing(Pair<String, Node>::first));

        RoaringBitmap fofs;
        RoaringBitmap friends;
        IntIterator intIterator;

        File file = new File(path);
        CsvWriter csvWriter = new CsvWriter();
        try (CsvAppender csvAppender = csvWriter.append(file, StandardCharsets.UTF_8)) {
            csvAppender.appendLine("user", "fof", "fic_count");
            int userCount = 0;
            for (Pair<String, Node> pair : users) {
                int fof;
                if (userCount++ > 1000) break; // short circuit for class
                fofs = new RoaringBitmap();
                friends = nodeFriends.get((int) pair.other().getId());
                intIterator = friends.getIntIterator();
                while (intIterator.hasNext()) {
                    fofs.or(nodeFriends.get(intIterator.next()));
                }
                fofs.remove((int) pair.other().getId());
                ArrayList<Pair<String, Integer>> counts = new ArrayList<>();
                intIterator = fofs.getIntIterator();
                while (intIterator.hasNext()) {
                    fof = intIterator.next();
                    int cardinality = RoaringBitmap.and(friends, nodeFriends.get(fof)).getCardinality();
                    if (cardinality <= 5) continue; // short circuit for class
                    counts.add(Pair.of(usernames.get(fof), cardinality));
                }
                counts.sort(Comparator.comparingInt(Pair<String, Integer>::other).reversed());
                for (Pair<String, Integer> count : counts) {
                    csvAppender.appendLine(pair.first(), count.first(), String.valueOf(count.other()));
                }
            }
        }
        return Stream.of(new StringResult("Report written to " + path));
    }

    @Procedure(name = "com.maxdemarzi.fic.distribution", mode = Mode.READ)
    @Description("com.maxdemarzi.fic.distribution")
    public Stream<CountValueResult> friendsInCommonDistribution() {
        long start = System.nanoTime();
        if (graph == null) {
            graph = this.db;
        }
        ResourceIterator userIterator = db.findNodes(Label.label("User"));

        Map<Long, long[]> instances = new HashMap<>();
        Roaring64NavigableMap seen = new Roaring64NavigableMap();
        int count = 0;
        while (userIterator.hasNext()) {
            Node user = (Node) userIterator.next();
            RoaringBitmap friendsMap = nodeFriends.get((int) user.getId());
            int[] friends = friendsMap.toArray();

            for (int i = 0; i < friends.length; i++) {
                for (int j = i + 1; j < friends.length; j++) {
                    long key = (((long)friends[i]) << 32) | (friends[j] & 0xffffffffL);
                    seen.add(key);
                }
            }
            if(count++ % 1000 == 0) {
                log.warn("On User # " + count + " at " + TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start));
            }
        }

        log.warn("Gathered combinations at " + TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start));
        count = 0;
        Iterator<Long> iterator = seen.iterator();
        long size = seen.getLongCardinality();
        while(iterator.hasNext()) {
            long l = iterator.next();
            int firstNodeId = (int)(l >> 32);
            int secondNodeId = (int)l;
            long common = RoaringBitmap.and(nodeFriends.get(firstNodeId), nodeFriends.get(secondNodeId)).getCardinality();
            instances.putIfAbsent(common, new long[]{0L});
            instances.get(common)[0]++;
            if(count++ % 50_000_000 == 0) {
                log.warn("On Combination # " + count + " of " + size + " at " + TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start));
            }
        }

        return instances.entrySet()
                .stream()
                .sorted(Comparator.comparingLong(Map.Entry<Long, long[]>::getKey))
                .map(entry -> new CountValueResult(entry.getKey(), entry.getValue()[0]));

    }
}
