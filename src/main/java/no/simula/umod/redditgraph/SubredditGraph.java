package no.simula.umod.redditgraph;


import akka.actor.ActorSystem;
import akka.stream.IOResult;
import akka.stream.alpakka.csv.javadsl.CsvFormatting;
import akka.stream.alpakka.csv.javadsl.CsvQuotingStyle;
import akka.stream.javadsl.Source;
import org.apache.commons.compress.compressors.CompressorException;
import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.alg.scoring.ClusteringCoefficient;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultUndirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTExporter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static no.simula.umod.redditgraph.ConsoleUtils.log;
import static no.simula.umod.redditgraph.ConsoleUtils.logDuration;

class SubRedditGraph {

    final Graph<SrVertex, Edge> g = new DefaultUndirectedWeightedGraph<>(Edge.class);
    // subreddit - Subreddit Vertex
    final Map<String, SrVertex> vertexMap =  new HashMap<>(10000);

    private final ActorSystem actorSystem;

    public SubRedditGraph(ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    public void createCountListFromCsv(File inputFile) throws IOException, CompressorException {
        long startTime = System.nanoTime();
        log("Start read csv to hashmap.");
        final var subredditUser = FileUtils.readAll(inputFile);
        final HashMap<String, HashSet<String>> users = new HashMap<>(10000000);

//        var source = Flows.getFileSource(inputFile);
//        var c = source
//                .via(CsvParsing.lineScanner())
//                .via(CsvToMap.toMapAsStrings(StandardCharsets.UTF_8))
//                .map(a -> {
//                    final var arr = new String[2];
//                    a.values().toArray(arr);
//                    return arr;
//                })
//                .runForeach(entry -> {
//                    // Create all vertices (duplicates handled by jgrapht)
//                    g.addVertex(new SrVertex(entry[0]));
//
//                    // Create subreddit list per user
//                    users.putIfAbsent(entry[1], new HashSet<>());
//                    users.get(entry[1]).add(entry[0]);
//                }, actorSystem);

//        c.toCompletableFuture().join();

        // the subreddit,user file
        for (final var entry : subredditUser) {

            // Create subreddit list per user
            users.putIfAbsent(entry[1], new HashSet<>());
            users.get(entry[1]).add(entry[0]);

            // Create a unique vertex per subreddit
            if (vertexMap.containsKey(entry[0]))
                continue;

            final var vertex = new SrVertex(entry[0]);
            vertexMap.put(entry[0],vertex);
            g.addVertex(vertex);
        }

        logDuration("Finished adding vertices and create user->sr list", startTime);
        startTime = System.nanoTime();

        // Add all the sr edges per user -> subreddit x subreddit
        users.forEach((user, subreddits) -> {
            // list of subreddits for user
            final String[] arr = new String[subreddits.size()];
            subreddits.toArray(arr);
            //            final arrsubreddits.toArray(new String[0]);

            subreddits.clear(); //todo: Does that make sense to "free up some ram"



            for (int i = 0; i < arr.length; i++) {
                for (int j = i + 1; j < arr.length; j++) {
                    // Undirected unique edge per user
                    final var src = vertexMap.get(arr[i]);
                    final var tar = vertexMap.get(arr[j]);
                    final var edge = g.getEdge(src, tar);
                    if (edge != null) {
                        edge.incrementNumberOfUsersInBothSubreddits();
                    } else {
                        g.addEdge(src, tar, new Edge());
                    }
                }
            }
        });

        logDuration("Finished building the graph", startTime);
        startTime = System.nanoTime();


        vertexMap.values().parallelStream().forEach(v -> {
            v.calculateScores(); // todo: calculate vertex score
            //todo: Calculate edge weights
            //todo Calculate vertex degree degree
        });

        logDuration("Finished calculating the vertex scores", startTime);

        log("# Vertices " + g.vertexSet().size());
        log("# Edges    " + g.edgeSet().size());
    }



    public CompletionStage<IOResult> exportEdgeList(File outFile) throws IOException, CompressorException {

        final var fileSink = Flows.getFileSink(outFile);
        final var csvFlow = CsvFormatting.format(CsvFormatting.COMMA,CsvFormatting.DOUBLE_QUOTE, CsvFormatting.BACKSLASH, "\n", CsvQuotingStyle.REQUIRED, StandardCharsets.UTF_8, Optional.empty());
        final var c = Source.from(g.edgeSet())
//                .mapAsyncUnordered(10, edge -> CompletableFuture.supplyAsync(() -> edge.getEdgeCsvLine()))
                .map(Edge::getEdgeCsvLine)
                .via(csvFlow)
                .async()
                .runWith(fileSink, actorSystem);
        return c;
    }

    public CompletableFuture exportDot(File outFile) throws IOException {
        //todo: Export Vertex attribute
        // degree
        // getSumOfEdgeWeightsConnectedToVertex() - weightedDegree
        // One loop:
        // degreeDegree (summe aller degrees der neighbouring vertex)
        // weightedDegreeDegree
        // local clustering coefficient


        return CompletableFuture.runAsync(() -> {
            final long startTime = System.nanoTime();
            final DOTExporter<SrVertex, Edge> exporter =
                    new DOTExporter<>(v -> '"' + v.toString().replace(".", "_") + '"');

            exporter.setVertexAttributeProvider(vertex -> {
                final Map<String, Attribute> map = new LinkedHashMap<>();
                map.put("degree", DefaultAttribute.createAttribute(g.degreeOf(vertex)));
                map.put("weightedDegree", DefaultAttribute.createAttribute(vertex.getSumOfEdgeWeightsConnectedToVertex()));

                return map;
            });

            exporter.setEdgeAttributeProvider(edge -> {
                final Map<String, Attribute> map = new LinkedHashMap<>();
                map.put("weight", DefaultAttribute.createAttribute(edge.getWeight()));

                return map;
            });


            try {
                final Writer writer = new FileWriter(outFile);
                exporter.exportGraph(g, writer);
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            logDuration("Exported dot", startTime);
        });
    }


    //todo: vertex degree, and weighted degree file. plot distribution.
    class SrVertex{

        private final String subreddit;
        private int sumOfEdgeWeightsConnectedToVertex = 0;



        private int degreeDegree = 0;
        private double weightedDegreeDegree = 0D;

        public SrVertex(String subreddit) {
            this.subreddit = subreddit;
        }

        @Override
        public String toString() {
            return subreddit;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SrVertex that = (SrVertex) o;
            return subreddit.equals(that.subreddit);
        }

        @Override
        public int hashCode() {
            return subreddit.hashCode();
        }

        /**
         * Sum up all the edge weights connected a vertex
         */
        private int calculateSumOfEdgeWeightsConnectedToVertex() {
            int sum = 0;

            for (Edge edge : g.edgesOf(this)) {
                sum += edge.getNumberOfUsersInBothSubreddits();
            }
            return sum;
        }

        public void calculateScores(){
            this.sumOfEdgeWeightsConnectedToVertex = calculateSumOfEdgeWeightsConnectedToVertex();
        }

        public void calcDegreeDegrees() {
            var ddd = 0;
            var wdd = 0D;
            for (var vtx : Graphs.neighborListOf(g, this)) {
                for (var edge : g.edgesOf(vtx)) {
                    wdd += edge.weight;
                    ddd += 1;
                }
            }
            degreeDegree = ddd;
            weightedDegreeDegree = wdd;
        }

        public int getSumOfEdgeWeightsConnectedToVertex() {
            return this.sumOfEdgeWeightsConnectedToVertex;
        }

        public void setSumOfEdgeWeightsConnectedToVertex(int sumOfEdgeWeightsConnectedToVertex) {
            this.sumOfEdgeWeightsConnectedToVertex = sumOfEdgeWeightsConnectedToVertex;
        }

        private double computeLocalClusteringCoefficient() {
            var neighbourhood = Graphs.neighborSetOf(g, this);
            final double k = neighbourhood.size();
            double numberTriplets = 0;
            for (var p : neighbourhood)
                for (var q : neighbourhood)
                    if (g.containsEdge(p, q))
                        numberTriplets++;
            if (k <= 1)
                return 0.0;
            else
                return numberTriplets / (k * (k - 1));
        }
    }

    class Edge extends DefaultWeightedEdge {

        private int numberOfUsersInThoseSubreddits = 1; // Uij
        private double weight;

        /**
         * Uij
         */
        public int getNumberOfUsersInBothSubreddits() {
            return numberOfUsersInThoseSubreddits;
        }
        /**
         * Uij
         */
        public void setNumberOfUsersInThoseSubreddits(int numberOfUsersInThoseSubreddits) {
            this.numberOfUsersInThoseSubreddits = numberOfUsersInThoseSubreddits;
        }

        public void incrementNumberOfUsersInBothSubreddits() {
            numberOfUsersInThoseSubreddits++;
        }

        /**
         * i
         */
        public SrVertex getSrc() {
            return (SrVertex) this.getSource();
        }

        /**
         * j
         * @return
         */
        public SrVertex getTar() {
            return (SrVertex) this.getTarget();
        }

        /**
         * degree i
         */
        public int getSourceDegree() {
            return g.degreeOf((SrVertex) getSource());
        }

        /**
         * degree j
         */
        public int getTargetDegree() {
            return g.degreeOf((SrVertex) getSource());
        }

        public int getWeightedSourceDegree() {
            return getSrc().getSumOfEdgeWeightsConnectedToVertex();
        }

        public int getWeightedTargetDegree() {
            return getSrc().getSumOfEdgeWeightsConnectedToVertex();
        }


        /**
         * Wij, stateful and cached. Works only if the weight has been calculated before.
         */
        @Override
        public double getWeight() {
            return calculateWeight( ge
        }

        public void calculateIndependentScores(){
            this.sumOfEdgeWeightsConnectedToVertex = calculateSumOfEdgeWeightsConnectedToVertex();
            // ToDo: Other scores
        }

        public void calculateScoresDependentOnVertex(){

            // ToDo: Other scores
        }

        /**
         * Wij
         */
        public double calculateWeight(final double avgi, final double avgj) {
            this.weight = (double)numberOfUsersInThoseSubreddits / (avgi + avgj); // todo: (avgi + avgj) / 2 ???
            return this.weight;
        }

        public Collection<String> getEdgeCsvLine() {
            final SrVertex source = getSrc();
            final SrVertex target = getTar();
            final int sourceDegree = g.degreeOf(source);
            final int targetDegree = g.degreeOf(target);
            final double weightedSourceDegree = source.getSumOfEdgeWeightsConnectedToVertex() / (double)sourceDegree;
            final double weightedTargetDegree = target.getSumOfEdgeWeightsConnectedToVertex() / (double)targetDegree;
            final double weight = calculateWeight(weightedSourceDegree, weightedTargetDegree);

            return List.of(
                    source.toString(), // i
                    target.toString(), // j
                    String.valueOf(numberOfUsersInThoseSubreddits), // Uij
                    String.valueOf(sourceDegree), // degree(i)
                    String.valueOf(targetDegree), // degree(j)
                    String.valueOf(weightedSourceDegree), // weightedDegree(i)
                    String.valueOf(weightedTargetDegree), // weightedDegree(j)
                    String.valueOf(weight) // Wij
            );
        }
    }
}

